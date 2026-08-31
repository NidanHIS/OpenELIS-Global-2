import React, { useContext, useEffect, useRef, useState } from "react";
import {
  Button,
  ComposedModal,
  DataTable,
  InlineLoading,
  ModalBody,
  ModalFooter,
  ModalHeader,
  Pagination,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TextInput,
} from "@carbon/react";
import { useHistory } from "react-router-dom";
import { FormattedMessage, useIntl } from "react-intl";
import { getFromOpenElisServerV2 } from "../utils/Utils";
import { NotificationContext } from "../layout/Layout";
import { AlertDialog, NotificationKinds } from "../common/CustomNotification";

export default function IncomingOrders() {
  const intl = useIntl();
  const componentMounted = useRef(false);
  const history = useHistory();

  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState([]);

  // ── SERVER-SIDE PAGINATION STATE ─────────────────────────────────────────────
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [totalCount, setTotalCount] = useState(0);
  const [search, setSearch] = useState("");

  const searchDebounceRef = useRef(null);
  const fetchSeqRef = useRef(0);

  // ── PAYWALL STATE ─────────────────────────────────────────────────────────────
  // Which row is currently being paywall-checked (shows spinner on that button)
  const [checkingOrderNumber, setCheckingOrderNumber] = useState(null);
  // Blocked modal
  const [blockedModalOpen, setBlockedModalOpen] = useState(false);

  const formatReceivedTimestamp = (timestamp) => {
    if (!timestamp) return "";
    try {
      const date = new Date(Number(timestamp));
      if (isNaN(date.getTime())) return "";
      const mm = String(date.getMonth() + 1).padStart(2, "0");
      const dd = String(date.getDate()).padStart(2, "0");
      const hh = String(date.getHours()).padStart(2, "0");
      const min = String(date.getMinutes()).padStart(2, "0");
      return `${mm}/${dd} ${hh}:${min}`;
    } catch (e) {
      return "";
    }
  };

  const headers = [
    {
      key: "patientName",
      header: intl.formatMessage({ id: "incomingOrders.table.patientName" }),
    },
    {
      key: "receivedTimestamp",
      header: intl.formatMessage({
        id: "incomingOrders.table.receivedTimestamp",
      }),
    },
    {
      key: "testCount",
      header: intl.formatMessage({ id: "incomingOrders.table.testCount" }),
    },
    {
      key: "source",
      header: intl.formatMessage({ id: "incomingOrders.table.source" }),
    },
    {
      key: "actions",
      header: intl.formatMessage({ id: "incomingOrders.table.actions" }),
    },
  ];

  const loadRows = (targetPage, targetPageSize, searchTerm) => {
    if (!componentMounted.current) return;

    const seq = ++fetchSeqRef.current;
    setLoading(true);

    const params = new URLSearchParams();
    params.set("page", String(targetPage));
    params.set("pageSize", String(targetPageSize));
    if (searchTerm && searchTerm.trim()) {
      params.set("search", searchTerm.trim());
    }

    getFromOpenElisServerV2(
      `/rest/incoming-orders/paged?${params.toString()}`,
    ).then((data) => {
      if (!componentMounted.current || seq !== fetchSeqRef.current) return;

      const list = Array.isArray(data?.items) ? data.items : [];
      const mapped = list.map((item) => {
        const rawName = String(item.patientName || "").trim();
        const formattedPatientName = rawName.includes(",")
          ? rawName
              .split(",")
              .map((s) => s.trim())
              .filter(Boolean)
              .reverse()
              .join(" ")
          : rawName;
        return {
          id: String(item.externalOrderNumber || ""),
          externalOrderNumber: item.externalOrderNumber || "",
          patientName: formattedPatientName,
          receivedTimestamp: formatReceivedTimestamp(item.receivedTimestamp),
          testCount: item.testCount != null ? String(item.testCount) : "",
          source: item.source || "",
        };
      });

      setRows(mapped);
      setTotalCount(data?.totalCount ?? 0);
      setLoading(false);
    });
  };

  useEffect(() => {
    componentMounted.current = true;
    loadRows(page, pageSize, search);
    return () => {
      componentMounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (!componentMounted.current) return;
    loadRows(page, pageSize, search);
  }, [page, pageSize]);

  useEffect(() => {
    if (searchDebounceRef.current) {
      clearTimeout(searchDebounceRef.current);
    }
    searchDebounceRef.current = setTimeout(() => {
      setPage(1);
      loadRows(1, pageSize, search);
    }, 350);
    return () => {
      if (searchDebounceRef.current) {
        clearTimeout(searchDebounceRef.current);
      }
    };
  }, [search]);

  /**
   * Collect click handler.
   * 1. Shows loading spinner on this row's button.
   * 2. Calls /paywall-check — one live call per click.
   * 3. blocked → show modal, stay on page.
   * 4. allowed / outage → navigate to SamplePatientEntry.
   */
  const onCollect = (row) => {
    if (!row) return;
    console.log("[PAYWALL] onCollect clicked for", row.id);

    const externalOrderNumber = row.id || row.externalOrderNumber || "";
    if (!externalOrderNumber) {
      setNotificationVisible(true);
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: "Missing external order number",
      });
      return;
    }

    setCheckingOrderNumber(externalOrderNumber);

    getFromOpenElisServerV2(
      `/rest/incoming-orders/${encodeURIComponent(externalOrderNumber)}/paywall-check`,
    )
      .then((data) => {
        setCheckingOrderNumber(null);
        if (data && data.blocked === true) {
          setBlockedModalOpen(true);
        } else {
          // allow or outage — proceed
          history.push(
            "/SamplePatientEntry?incomingOrderNumber=" +
              encodeURIComponent(externalOrderNumber),
          );
        }
      })
      .catch(() => {
        // network error — fail-open
        setCheckingOrderNumber(null);
        history.push(
          "/SamplePatientEntry?incomingOrderNumber=" +
            encodeURIComponent(externalOrderNumber),
        );
      });
  };

  return (
    <>
      {notificationVisible === true ? <AlertDialog /> : ""}

      {/* ── Payment blocked modal ─────────────────────────────────────────── */}
      <ComposedModal
        open={blockedModalOpen}
        onClose={() => setBlockedModalOpen(false)}
      >
        <ModalHeader
          title={intl.formatMessage({
            id: "paywall.blocked.title",
            defaultMessage: "Payment Required",
          })}
        />
        <ModalBody>
          <p>
            {intl.formatMessage({
              id: "paywall.blocked.message",
              defaultMessage:
                "This patient has an outstanding balance. Please settle payment before collecting the sample.",
            })}
          </p>
        </ModalBody>
        <ModalFooter>
          <Button kind="primary" onClick={() => setBlockedModalOpen(false)}>
            {intl.formatMessage({
              id: "label.button.ok",
              defaultMessage: "OK",
            })}
          </Button>
        </ModalFooter>
      </ComposedModal>

      <TableContainer
        title={intl.formatMessage({ id: "incomingOrders.title" })}
        description={intl.formatMessage({ id: "incomingOrders.description" })}
      >
        {loading ? (
          <InlineLoading
            description={intl.formatMessage({ id: "incomingOrders.loading" })}
          />
        ) : null}

        <div style={{ padding: "0.75rem 0 0.5rem" }}>
          <TextInput
            id="incoming-orders-search"
            labelText=""
            placeholder={intl.formatMessage({
              id: "incomingOrders.search.placeholder",
              defaultMessage: "Search by order number…",
            })}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ maxWidth: "24rem" }}
          />
        </div>

        <DataTable rows={rows} headers={headers} isSortable={false}>
          {({ rows, headers, getHeaderProps, getTableProps }) => (
            <Table {...getTableProps()}>
              <TableHead>
                <TableRow>
                  {headers.map((header) => (
                    <TableHeader
                      key={header.key}
                      {...getHeaderProps({ header })}
                    >
                      {header.header}
                    </TableHeader>
                  ))}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => (
                  <TableRow key={row.id}>
                    {row.cells.map((cell) => {
                      if (cell.info.header === "actions") {
                        const isChecking = checkingOrderNumber === row.id;
                        return (
                          <TableCell key={cell.id}>
                            <Button
                              size="sm"
                              kind="primary"
                              disabled={loading || isChecking}
                              onClick={() => onCollect(row)}
                            >
                              {isChecking ? (
                                <InlineLoading status="active" />
                              ) : (
                                <FormattedMessage id="incomingOrders.collect" />
                              )}
                            </Button>
                          </TableCell>
                        );
                      }
                      return <TableCell key={cell.id}>{cell.value}</TableCell>;
                    })}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </DataTable>

        <Pagination
          onChange={({ page: p, pageSize: ps }) => {
            setPage(p);
            setPageSize(ps);
          }}
          page={page}
          pageSize={pageSize}
          pageSizes={[10, 20, 40, 50]}
          totalItems={totalCount}
          forwardText={intl.formatMessage({ id: "pagination.forward" })}
          backwardText={intl.formatMessage({ id: "pagination.backward" })}
          itemRangeText={(min, max, total) =>
            intl.formatMessage(
              { id: "pagination.item-range" },
              { min, max, total },
            )
          }
          itemsPerPageText={intl.formatMessage({
            id: "pagination.items-per-page",
          })}
          itemText={(min, max) =>
            intl.formatMessage({ id: "pagination.item" }, { min, max })
          }
          pageNumberText={intl.formatMessage({
            id: "pagination.page-number",
          })}
          pageRangeText={(_c, total) =>
            intl.formatMessage({ id: "pagination.page-range" }, { total })
          }
          pageText={(p, unk) =>
            intl.formatMessage(
              { id: "pagination.page" },
              { page: unk ? "" : p },
            )
          }
        />
      </TableContainer>
    </>
  );
}
