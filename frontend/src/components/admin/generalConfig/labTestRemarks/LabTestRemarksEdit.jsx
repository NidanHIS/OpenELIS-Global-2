import React, { useState, useEffect, useContext } from "react";
import {
  Button,
  ComboBox,
  Grid,
  Column,
  Section,
  Heading,
  TextArea,
  Loading,
  Pagination,
} from "@carbon/react";
import { TrashCan, Add } from "@carbon/icons-react";
import {
  getFromOpenElisServer,
  postToOpenElisServer,
} from "../../../utils/Utils.js";
import { NotificationContext } from "../../../layout/Layout.js";
import {
  AlertDialog,
  NotificationKinds,
} from "../../../common/CustomNotification.js";
import { FormattedMessage, useIntl } from "react-intl";

/**
 * Custom editor for the "labTestRemarks" site_information entry.
 *
 * Flexbox Card Layout:
 * - Header row (fixed height, bg #f4f4f4).
 * - Body rowgroup (flex: 1 1 auto, minHeight: 0, overflowY: auto) fills all remaining card height.
 * - Spacer div (flex: 1) absorbs leftover height below rendered rows.
 * - Footer (fixed height, pinned) with Add Row button and Carbon Pagination.
 */
const LabTestRemarksEdit = () => {
  const intl = useIntl();
  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const [rows, setRows] = useState([]);         // { rowKey, id, entityType, entityId, entityName, remarks }
  const [deletedIds, setDeletedIds] = useState([]); // IDs to delete explicitly on Save
  const [options, setOptions] = useState([]);   // { entityType, entityId, displayName }
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);
  const [rowCounter, setRowCounter] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  // ── Load data on mount ───────────────────────────────────────────────────

  useEffect(() => {
    let mounted = true;
    setIsLoading(true);

    let savedRows = null;
    let optionsList = null;

    const trySetData = () => {
      if (savedRows !== null && optionsList !== null) {
        if (mounted) {
          setRows(
            savedRows.map((r, idx) => ({
              rowKey: `existing-${r.id}-${idx}`,
              id: r.id,
              entityType: r.entityType,
              entityId: r.entityId,
              entityName: r.entityName,
              remarks: r.remarks || "",
            }))
          );
          setOptions(optionsList);
          setIsLoading(false);
        }
      }
    };

    getFromOpenElisServer("/rest/nidanLabTestRemarks", (res) => {
      savedRows = Array.isArray(res) ? res : [];
      trySetData();
    });

    getFromOpenElisServer("/rest/nidanLabTestRemarks/options", (res) => {
      optionsList = Array.isArray(res) ? res : [];
      trySetData();
    });

    return () => {
      mounted = false;
    };
  }, []);

  // ── Set of currently selected entityType+entityId combos ────────────────

  const selectedKeys = new Set(
    rows
      .filter((r) => r.entityType && r.entityId)
      .map((r) => `${r.entityType}:${r.entityId}`)
  );

  const getAvailableOptions = (row) => {
    return options.filter((opt) => {
      const key = `${opt.entityType}:${opt.entityId}`;
      const isThisRow =
        row.entityType === opt.entityType && row.entityId === opt.entityId;
      return isThisRow || !selectedKeys.has(key);
    });
  };

  // ── Row Operations ───────────────────────────────────────────────────────

  const addRow = () => {
    const newKey = `new-${rowCounter}`;
    setRowCounter((c) => c + 1);
    setRows((prev) => [
      ...prev,
      {
        rowKey: newKey,
        id: null,
        entityType: null,
        entityId: null,
        entityName: null,
        remarks: "",
      },
    ]);
  };

  const deleteRow = (targetRow) => {
    if (targetRow.id) {
      setDeletedIds((prev) => [...prev, targetRow.id]);
    }
    setRows((prev) => prev.filter((r) => r.rowKey !== targetRow.rowKey));
  };

  const updateRowEntity = (rowKey, selectedItem) => {
    if (!selectedItem) return;
    setRows((prev) =>
      prev.map((r) =>
        r.rowKey === rowKey
          ? {
              ...r,
              entityType: selectedItem.entityType,
              entityId: selectedItem.entityId,
              entityName: selectedItem.displayName.replace(/^\[(?:Test|Panel)\] /, ""),
            }
          : r
      )
    );
  };

  const updateRowRemarks = (rowKey, value) => {
    setRows((prev) =>
      prev.map((r) =>
        r.rowKey === rowKey ? { ...r, remarks: value } : r
      )
    );
  };

  // ── Save ─────────────────────────────────────────────────────────────────

  const handleSave = () => {
    const incomplete = rows.some((r) => !r.entityType || !r.entityId);
    if (incomplete) {
      setNotificationVisible(true);
      addNotification({
        kind: NotificationKinds.error,
        title: intl.formatMessage({ id: "notification.title" }),
        message: "Please select a Test or Panel for every row, or remove unassigned rows before saving.",
      });
      return;
    }

    setIsSaving(true);

    const payload = {
      deletedIds: deletedIds,
      itemsToSave: rows.map((r) => ({
        id: r.id,
        entityType: r.entityType,
        entityId: r.entityId,
        remarks: r.remarks,
      })),
    };

    const body = JSON.stringify(payload);

    postToOpenElisServer("/rest/nidanLabTestRemarks/save", body, (status) => {
      setIsSaving(false);
      if (status === 200) {
        setNotificationVisible(true);
        addNotification({
          kind: NotificationKinds.success,
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({ id: "save.config.success.msg" }),
        });
        setTimeout(() => window.location.reload(), 800);
      } else {
        setNotificationVisible(true);
        addNotification({
          kind: NotificationKinds.error,
          title: intl.formatMessage({ id: "notification.title" }),
          message: intl.formatMessage({ id: "server.error.msg" }),
        });
      }
    });
  };

  // ── Render ───────────────────────────────────────────────────────────────

  if (isLoading) {
    return (
      <div className="adminPageContent">
        <Loading description="Loading Lab Test Remarks..." />
      </div>
    );
  }

  const pagedRows = rows.slice((currentPage - 1) * pageSize, currentPage * pageSize);

  return (
    <div className="adminPageContent">
      {notificationVisible && <AlertDialog />}

      {/* Header */}
      <Grid>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Heading style={{ marginBottom: "8px" }}>Lab Test Remarks</Heading>
          </Section>
        </Column>
      </Grid>

      {/* Action Buttons */}
      <Grid style={{ marginBottom: "12px" }}>
        <Column lg={16} md={8} sm={4}>
          <Button
            data-cy="lab-test-remarks-save"
            onClick={handleSave}
            disabled={isSaving}
            style={{ marginRight: "8px" }}
          >
            {isSaving ? "Saving..." : <FormattedMessage id="admin.page.configuration.formEntryConfigMenu.button.save" />}
          </Button>
          <Button
            data-cy="lab-test-remarks-back"
            kind="secondary"
            onClick={() => window.location.reload()}
          >
            <FormattedMessage id="admin.page.configuration.formEntryConfigMenu.button.exit" />
          </Button>
        </Column>
      </Grid>

      {/* Flexbox Card Container */}
      <div className="orderLegendBody">
        <div
          className="gridBoundary"
          style={{
            display: "flex",
            flexDirection: "column",
            height: "calc(100vh - 230px)",
            border: "1px solid #d1d1d1",
            borderRadius: "4px",
            backgroundColor: "#ffffff",
            overflow: "hidden",
          }}
        >
          {/* Header row — fixed height, never grows/shrinks */}
          <div
            role="row"
            style={{
              display: "grid",
              gridTemplateColumns: "60px 35% 1fr 50px",
              backgroundColor: "#f4f4f4",
              fontWeight: 600,
              padding: "12px 16px",
              flexShrink: 0,
              borderBottom: "1px solid #e0e0e0",
            }}
          >
            <div>S.No.</div>
            <div>Test/Panel Name</div>
            <div>Remarks</div>
            <div></div>
          </div>

          {/* Body — flex: 1 GUARANTEES this fills all remaining card height */}
          <div
            role="rowgroup"
            style={{
              flex: "1 1 auto",
              display: "flex",
              flexDirection: "column",
              minHeight: 0,
              overflowY: "auto",
            }}
          >
            {rows.length === 0 ? (
              <div
                style={{
                  flex: 1,
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  color: "#525252",
                }}
              >
                <div style={{ fontSize: "15px", fontWeight: 600, marginBottom: "4px" }}>
                  No Lab Test Remarks Configured
                </div>
                <div style={{ fontSize: "13px", color: "#8d8d8d" }}>
                  Click &quot;+ Add Row&quot; below to assign remarks to a test or panel.
                </div>
              </div>
            ) : (
              <>
                {pagedRows.map((row, idx) => {
                  const availableOpts = getAvailableOptions(row);
                  const selectedOpt = row.entityId
                    ? options.find(
                        (o) =>
                          o.entityType === row.entityType &&
                          o.entityId === row.entityId
                      ) || {
                        entityType: row.entityType,
                        entityId: row.entityId,
                        displayName: row.entityName || `[Unknown ${row.entityType} (ID: ${row.entityId})]`,
                      }
                    : null;

                  const isLimitExceeded = row.remarks && row.remarks.length >= 2000;
                  const trueIdx = (currentPage - 1) * pageSize + idx;

                  return (
                    <div
                      key={row.rowKey}
                      role="row"
                      style={{
                        display: "grid",
                        gridTemplateColumns: "60px 35% 1fr 50px",
                        padding: "10px 16px",
                        borderBottom: "1px solid #e0e0e0",
                        alignItems: "start",
                      }}
                    >
                      <div style={{ paddingTop: "10px" }}>{trueIdx + 1}</div>
                      <div>
                        <ComboBox
                          id={`combo-${row.rowKey}`}
                          items={availableOpts}
                          itemToString={(item) => (item ? item.displayName : "")}
                          selectedItem={selectedOpt}
                          onChange={({ selectedItem }) =>
                            updateRowEntity(row.rowKey, selectedItem)
                          }
                          placeholder="Search test or panel..."
                          titleText=""
                          autoAlign
                          style={{ width: "100%" }}
                        />
                      </div>
                      <div style={{ paddingRight: "12px" }}>
                        <TextArea
                          id={`remarks-${row.rowKey}`}
                          labelText=""
                          value={row.remarks}
                          onChange={(e) =>
                            updateRowRemarks(row.rowKey, e.target.value)
                          }
                          maxLength={2000}
                          rows={2}
                          invalid={isLimitExceeded}
                          invalidText={
                            isLimitExceeded
                              ? "Maximum limit of 2000 characters reached."
                              : ""
                          }
                          style={{ width: "100%" }}
                        />
                      </div>
                      <div style={{ paddingTop: "6px" }}>
                        <Button
                          kind="ghost"
                          size="sm"
                          hasIconOnly
                          renderIcon={TrashCan}
                          iconDescription="Delete row"
                          onClick={() => deleteRow(row)}
                          data-cy={`delete-row-${row.rowKey}`}
                        />
                      </div>
                    </div>
                  );
                })}
                {/* Spacer eats leftover space below the last row — flex handles this natively */}
                <div style={{ flex: 1 }} />
              </>
            )}
          </div>

          {/* Footer — pinned, fixed height */}
          <div
            style={{
              padding: "12px 16px",
              borderTop: "1px solid #e0e0e0",
              flexShrink: 0,
              display: "flex",
              justify: "space-between",
              alignItems: "center",
              backgroundColor: "#ffffff",
            }}
          >
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Add}
              onClick={addRow}
              data-cy="lab-test-remarks-add-row"
            >
              Add Row
            </Button>
            {rows.length > pageSize && (
              <Pagination
                page={currentPage}
                pageSize={pageSize}
                pageSizes={[5, 10, 20]}
                totalItems={rows.length}
                onChange={({ page, pageSize: newSize }) => {
                  setCurrentPage(page);
                  setPageSize(newSize);
                }}
              />
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default LabTestRemarksEdit;
