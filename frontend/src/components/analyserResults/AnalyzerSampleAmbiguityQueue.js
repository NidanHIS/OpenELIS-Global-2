import React, {
  useState,
  useEffect,
  useRef,
  useContext,
  useCallback,
  useMemo,
} from "react";
import {
  Button,
  Column,
  Grid,
  InlineLoading,
  InlineNotification,
  Tag,
  Tile,
} from "@carbon/react";
import { CheckmarkFilled, TrashCan, Warning } from "@carbon/icons-react";
import { FormattedMessage, useIntl } from "react-intl";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../utils/Utils";
import { NotificationContext } from "../layout/Layout";
import { NotificationKinds } from "../common/CustomNotification";

const PENDING_ENDPOINT = "/rest/nidan/analyzer-ambiguity/pending";
const RESOLVE_ENDPOINT = "/rest/nidan/analyzer-ambiguity/resolve";
const DISCARD_ENDPOINT = "/rest/nidan/analyzer-ambiguity/discard";

/**
 * Governing queue for 4-digit sample number ambiguity.
 *
 * Shows all PENDING ambiguous analyzer results and lets the user either:
 *  - Choose one candidate sample  →  result moves to analyzer_results holding
 *  - Discard the result entirely  →  row marked DISCARDED, nothing written
 */
const AnalyzerSampleAmbiguityQueue = () => {
  const componentMounted = useRef(false);
  const intl = useIntl();

  const { setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const [cards, setCards] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(null);
  // Per-card action state: { [pendingId]: "resolving" | "discarding" | "error:<msg>" }
  const [actionState, setActionState] = useState({});

  const groupedCards = useMemo(() => {
    const groups = new Map();
    cards.forEach((card) => {
      const dateKey =
        card.resultDate ||
        intl.formatMessage({
          id: "ambiguityQueue.date.unknown",
          defaultMessage: "Unspecified Date",
        });
      if (!groups.has(dateKey)) {
        groups.set(dateKey, []);
      }
      groups.get(dateKey).push(card);
    });
    return Array.from(groups.entries());
  }, [cards, intl]);

  // ── Load pending cards ────────────────────────────────────────────────────

  const loadPending = useCallback(() => {
    setLoading(true);
    setLoadError(null);
    getFromOpenElisServer(PENDING_ENDPOINT, (data) => {
      if (!componentMounted.current) return;
      if (!data) {
        setLoadError(
          intl.formatMessage({
            id: "ambiguityQueue.error.load",
            defaultMessage: "Failed to load pending items",
          }),
        );
        setCards([]);
      } else {
        setCards(Array.isArray(data) ? data : []);
      }
      setLoading(false);
    });
  }, [intl]);

  useEffect(() => {
    componentMounted.current = true;
    loadPending();
    return () => {
      componentMounted.current = false;
    };
  }, [loadPending]);

  // ── Resolve action ────────────────────────────────────────────────────────

  const handleResolve = (pendingId, sampleId, sampleNumber, accession) => {
    setActionState((prev) => ({ ...prev, [pendingId]: "resolving" }));
    const payload = JSON.stringify({ pendingId, sampleId });
    postToOpenElisServerJsonResponse(RESOLVE_ENDPOINT, payload, (response) => {
      if (!componentMounted.current) return;
      if (response && (response.message || !response.error)) {
        // Success: remove card, notify
        setCards((prev) => prev.filter((c) => c.id !== pendingId));
        setActionState((prev) => {
          const n = { ...prev };
          delete n[pendingId];
          return n;
        });
        addNotification({
          kind: NotificationKinds.success,
          title: intl.formatMessage({
            id: "ambiguityQueue.resolve.success.title",
            defaultMessage: "Resolved",
          }),
          message: intl.formatMessage(
            {
              id: "ambiguityQueue.resolve.success.msg",
              defaultMessage:
                "Result linked to sample {sampleNumber} ({accession})",
            },
            {
              sampleNumber: sampleNumber || sampleId,
              accession: accession || "",
            },
          ),
        });
        setNotificationVisible(true);
      } else {
        // Error: show inline error on card
        const msg =
          (response && response.error) ||
          intl.formatMessage({
            id: "ambiguityQueue.error.resolve",
            defaultMessage: "Failed to resolve",
          });
        setActionState((prev) => ({ ...prev, [pendingId]: `error:${msg}` }));
      }
    });
  };

  // ── Discard action ────────────────────────────────────────────────────────

  const handleDiscard = (pendingId) => {
    setActionState((prev) => ({ ...prev, [pendingId]: "discarding" }));
    const payload = JSON.stringify({ pendingId });
    postToOpenElisServerJsonResponse(DISCARD_ENDPOINT, payload, (response) => {
      if (!componentMounted.current) return;
      if (response && (response.message || !response.error)) {
        setCards((prev) => prev.filter((c) => c.id !== pendingId));
        setActionState((prev) => {
          const n = { ...prev };
          delete n[pendingId];
          return n;
        });
        addNotification({
          kind: NotificationKinds.info,
          title: intl.formatMessage({
            id: "ambiguityQueue.discard.success.title",
            defaultMessage: "Discarded",
          }),
          message: intl.formatMessage({
            id: "ambiguityQueue.discard.success.msg",
            defaultMessage: "Ambiguous result discarded",
          }),
        });
        setNotificationVisible(true);
      } else {
        const msg =
          (response && response.error) ||
          intl.formatMessage({
            id: "ambiguityQueue.error.discard",
            defaultMessage: "Failed to discard",
          });
        setActionState((prev) => ({ ...prev, [pendingId]: `error:${msg}` }));
      }
    });
  };

  // ── Render ────────────────────────────────────────────────────────────────

  return (
    <Grid fullWidth className="analyzerAmbiguityQueue">
      {/* Header */}
      <Column lg={16} md={8} sm={4}>
        <h2 style={{ marginBottom: "0.5rem" }}>
          <FormattedMessage
            id="ambiguityQueue.title"
            defaultMessage="Machine Results"
          />
        </h2>
        <p style={{ color: "#525252", marginBottom: "1.5rem" }}>
          <FormattedMessage
            id="ambiguityQueue.subtitle"
            defaultMessage="Review and resolve incoming machine results that require manual matching or disambiguation."
          />
        </p>
      </Column>

      {/* Loading */}
      {loading && (
        <Column lg={16} md={8} sm={4}>
          <InlineLoading
            description={intl.formatMessage({
              id: "ambiguityQueue.loading",
              defaultMessage: "Loading pending items...",
            })}
          />
        </Column>
      )}

      {/* Load error */}
      {!loading && loadError && (
        <Column lg={16} md={8} sm={4}>
          <InlineNotification
            kind="error"
            title={intl.formatMessage({
              id: "ambiguityQueue.error.title",
              defaultMessage: "Error",
            })}
            subtitle={loadError}
            actions={
              <Button kind="ghost" size="sm" onClick={loadPending}>
                <FormattedMessage
                  id="ambiguityQueue.retry"
                  defaultMessage="Retry"
                />
              </Button>
            }
            lowContrast
          />
        </Column>
      )}

      {/* Empty state */}
      {!loading && !loadError && cards.length === 0 && (
        <Column lg={16} md={8} sm={4}>
          <Tile style={{ textAlign: "center", padding: "2rem" }}>
            <CheckmarkFilled
              size={32}
              style={{ color: "#198038", marginBottom: "0.75rem" }}
            />
            <p style={{ fontSize: "1rem", fontWeight: 600 }}>
              <FormattedMessage
                id="ambiguityQueue.empty"
                defaultMessage="No pending ambiguous results — all clear."
              />
            </p>
          </Tile>
        </Column>
      )}

      {/* Cards grouped by incoming result date */}
      {!loading &&
        groupedCards.map(([dateKey, groupCards]) => (
          <React.Fragment key={dateKey}>
            <Column
              lg={16}
              md={8}
              sm={4}
              style={{ marginTop: "1rem", marginBottom: "0.75rem" }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "0.75rem",
                  borderBottom: "1px solid #e0e0e0",
                  paddingBottom: "0.5rem",
                }}
              >
                <span
                  style={{
                    fontWeight: 700,
                    fontSize: "1.05rem",
                    color: "#161616",
                  }}
                >
                  📅 {dateKey}
                </span>
                <Tag type="cool-gray" size="sm">
                  {groupCards.length}{" "}
                  <FormattedMessage
                    id="ambiguityQueue.group.count"
                    defaultMessage="result(s)"
                  />
                </Tag>
              </div>
            </Column>
            {groupCards.map((card) => {
              const state = actionState[card.id];
              const isActing = state === "resolving" || state === "discarding";
              const cardError =
                state && state.startsWith("error:") ? state.slice(6) : null;

              return (
                <Column
                  key={card.id}
                  lg={16}
                  md={8}
                  sm={4}
                  style={{ marginBottom: "1rem" }}
                >
                  <Tile
                    style={{
                      borderLeft: "4px solid #f1c21b",
                      borderRadius: "4px",
                    }}
                  >
                    {/* Card header: identifier + meta tags */}
                    <div
                      style={{
                        display: "flex",
                        alignItems: "center",
                        flexWrap: "wrap",
                        gap: "0.5rem",
                        marginBottom: "0.75rem",
                      }}
                    >
                      <Warning size={20} style={{ color: "#f1c21b" }} />
                      <span style={{ fontWeight: 700, fontSize: "1.1rem" }}>
                        <FormattedMessage
                          id="ambiguityQueue.card.identifier"
                          defaultMessage="Identifier:"
                        />{" "}
                        <code
                          style={{
                            background: "#f4f4f4",
                            padding: "2px 6px",
                            borderRadius: 3,
                          }}
                        >
                          {card.incomingIdentifier}
                        </code>
                      </span>
                      {card.analyzerId && (
                        <Tag type="cool-gray" size="sm">
                          {card.analyzerId}
                        </Tag>
                      )}
                      {card.resultDate && (
                        <Tag type="gray" size="sm">
                          {card.resultDate}
                        </Tag>
                      )}
                      {card.analyzerTestCode && (
                        <Tag type="blue" size="sm">
                          {card.analyzerTestCode}
                        </Tag>
                      )}
                      {card.testName && card.testName !== card.analyzerTestCode && (
                        <Tag type="teal" size="sm">
                          {card.testName}
                        </Tag>
                      )}
                      {card.resultValue != null && (
                        <Tag type="green" size="sm">
                          {card.resultValue}
                          {card.resultUnits ? ` ${card.resultUnits}` : ""}
                        </Tag>
                      )}
                    </div>

                    {/* Inline card error */}
                    {cardError && (
                      <InlineNotification
                        kind="error"
                        title={intl.formatMessage({
                          id: "ambiguityQueue.error.action",
                          defaultMessage: "Action failed",
                        })}
                        subtitle={cardError}
                        lowContrast
                        style={{ marginBottom: "0.75rem" }}
                        onCloseButtonClick={() =>
                          setActionState((prev) => {
                            const n = { ...prev };
                            delete n[card.id];
                            return n;
                          })
                        }
                      />
                    )}

                    {/* Candidate list */}
                    {card.candidates && card.candidates.length === 1 && (
                      <div style={{ marginBottom: "0.5rem" }}>
                        <Tag type="cyan" size="sm">
                          <FormattedMessage
                            id="ambiguityQueue.singleCandidate"
                            defaultMessage="1 Candidate Remaining (Conflict Resolved)"
                          />
                        </Tag>
                      </div>
                    )}

                    {(!card.candidates || card.candidates.length === 0) ? (
                      <InlineNotification
                        kind="warning"
                        title={intl.formatMessage({
                          id: "ambiguityQueue.noCandidates.title",
                          defaultMessage: "No Open Orders Remaining",
                        })}
                        subtitle={intl.formatMessage({
                          id: "ambiguityQueue.noCandidates.subtitle",
                          defaultMessage:
                            "All candidate orders for this test have already been claimed or finalized. You may discard this result.",
                        })}
                        lowContrast
                        hideCloseButton
                        style={{ marginBottom: "0.75rem" }}
                      />
                    ) : (
                      <p
                        style={{
                          fontWeight: 600,
                          marginBottom: "0.5rem",
                          color: "#525252",
                          fontSize: "0.875rem",
                        }}
                      >
                        <FormattedMessage
                          id="ambiguityQueue.candidates.label"
                          defaultMessage="{count} candidate sample(s) — choose the correct one:"
                          values={{ count: (card.candidates || []).length }}
                        />
                      </p>
                    )}

                    <div
                      style={{
                        display: "flex",
                        flexDirection: "column",
                        gap: "0.5rem",
                        marginBottom: "1rem",
                      }}
                    >
                      {(card.candidates || []).map((c) => (
                        <div
                          key={c.sampleId}
                          style={{
                            display: "flex",
                            alignItems: "center",
                            flexWrap: "wrap",
                            gap: "0.75rem",
                            background: "#f4f4f4",
                            borderRadius: "4px",
                            padding: "0.5rem 0.75rem",
                          }}
                        >
                          <span style={{ fontWeight: 600, minWidth: "90px" }}>
                            #{c.sampleNumber || c.sampleId}
                          </span>
                          {c.orderDate && (
                            <span
                              style={{ color: "#525252", fontSize: "0.875rem" }}
                            >
                              <FormattedMessage
                                id="sample.label.orderdate"
                                defaultMessage="Order Date:"
                              />{" "}
                              <span style={{ fontWeight: 600, color: "#161616" }}>
                                {c.orderDate}
                              </span>
                            </span>
                          )}
                          {c.accessionNumber && (
                            <span
                              style={{ color: "#525252", fontSize: "0.875rem" }}
                            >
                              <FormattedMessage
                                id="ambiguityQueue.accession"
                                defaultMessage="Acc:"
                              />{" "}
                              {c.accessionNumber}
                            </span>
                          )}
                          {c.patientName && (
                            <span style={{ fontSize: "0.875rem" }}>
                              👤 {c.patientName}
                              {c.patientId ? ` (${c.patientId})` : ""}
                            </span>
                          )}
                          {c.orderedTests && c.orderedTests.length > 0 && (
                            <span style={{ fontSize: "0.8rem", color: "#6f6f6f" }}>
                              🧪 {c.orderedTests.join(", ")}
                            </span>
                          )}
                          <Button
                            kind="primary"
                            size="sm"
                            disabled={isActing}
                            onClick={() =>
                              handleResolve(
                                card.id,
                                c.sampleId,
                                c.sampleNumber,
                                c.accessionNumber,
                              )
                            }
                            style={{ marginLeft: "auto" }}
                          >
                            {state === "resolving" ? (
                              <InlineLoading description="" />
                            ) : (
                              <>
                                <CheckmarkFilled size={16} />
                                &nbsp;
                                <FormattedMessage
                                  id="ambiguityQueue.resolve.btn"
                                  defaultMessage="Use this sample"
                                />
                              </>
                            )}
                          </Button>
                        </div>
                      ))}
                    </div>

                    {/* Discard button */}
                    <div style={{ display: "flex", justifyContent: "flex-end" }}>
                      <Button
                        kind="danger--ghost"
                        size="sm"
                        disabled={isActing}
                        renderIcon={TrashCan}
                        onClick={() => handleDiscard(card.id)}
                      >
                        {state === "discarding" ? (
                          <InlineLoading
                            description={intl.formatMessage({
                              id: "ambiguityQueue.discarding",
                              defaultMessage: "Discarding...",
                            })}
                          />
                        ) : (
                          <FormattedMessage
                            id="ambiguityQueue.discard.btn"
                            defaultMessage="Discard result"
                          />
                        )}
                      </Button>
                    </div>
                  </Tile>
                </Column>
              );
            })}
          </React.Fragment>
        ))}

      {/* Refresh button when cards are present */}
      {!loading && cards.length > 0 && (
        <Column lg={16} md={8} sm={4} style={{ marginTop: "1rem" }}>
          <Button
            kind="ghost"
            size="sm"
            onClick={loadPending}
            disabled={loading}
          >
            <FormattedMessage
              id="ambiguityQueue.refresh"
              defaultMessage="Refresh queue"
            />
          </Button>
        </Column>
      )}
    </Grid>
  );
};

export default AnalyzerSampleAmbiguityQueue;
