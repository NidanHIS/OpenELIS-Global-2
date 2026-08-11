import React, { useState, useEffect, useContext } from "react";
import {
  Button,
  ComboBox,
  Grid,
  Column,
  Section,
  Heading,
  Table,
  TableHead,
  TableRow,
  TableHeader,
  TableBody,
  TableCell,
  TextArea,
  Loading,
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
 * Table: S.No | Test/Panel Name | Remarks | Delete
 *
 * - Table starts empty; user adds rows by picking a test/panel from the dropdown
 * - Test/Panel names are dynamic
 * - Delete tracks deletedIds for granular DB deletion on Save
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
    // If it was already saved in DB (has an id), track its ID for deletion
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
        message: "Please select a Test or Panel for every row before saving.",
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

  return (
    <div className="adminPageContent">
      {notificationVisible && <AlertDialog />}

      {/* Header */}
      <Grid>
        <Column lg={16} md={8} sm={4}>
          <Section>
            <Heading>Lab Test Remarks</Heading>
          </Section>
          <br />
        </Column>
      </Grid>

      {/* Action Buttons */}
      <Grid>
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

      <br />

      {/* Table */}
      <div className="orderLegendBody">
        <div className="gridBoundary">
          <div style={{ overflowX: "auto" }}>
            <Table>
              <TableHead>
                <TableRow>
                  <TableHeader style={{ width: "60px" }}>S.No.</TableHeader>
                  <TableHeader style={{ width: "320px" }}>Test/Panel Name</TableHeader>
                  <TableHeader>Remarks</TableHeader>
                  <TableHeader style={{ width: "60px" }}></TableHeader>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} style={{ textAlign: "center", color: "#6f6f6f", padding: "24px" }}>
                      No remarks yet. Click &quot;Add Row&quot; to get started.
                    </TableCell>
                  </TableRow>
                )}
                {rows.map((row, idx) => {
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

                  return (
                    <TableRow key={row.rowKey}>
                      {/* S.No */}
                      <TableCell style={{ verticalAlign: "top", paddingTop: "20px" }}>
                        {idx + 1}
                      </TableCell>

                      {/* Test/Panel Name — searchable ComboBox */}
                      <TableCell style={{ verticalAlign: "top" }}>
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
                          style={{ minWidth: "280px" }}
                        />
                      </TableCell>

                      {/* Remarks — free text, max 2000 chars */}
                      <TableCell style={{ verticalAlign: "top" }}>
                        <TextArea
                          id={`remarks-${row.rowKey}`}
                          labelText=""
                          value={row.remarks}
                          onChange={(e) =>
                            updateRowRemarks(row.rowKey, e.target.value)
                          }
                          maxCount={2000}
                          enableCounter
                          rows={3}
                          style={{ minWidth: "300px" }}
                        />
                      </TableCell>

                      {/* Delete Icon */}
                      <TableCell style={{ verticalAlign: "top", paddingTop: "16px" }}>
                        <Button
                          kind="ghost"
                          size="sm"
                          hasIconOnly
                          renderIcon={TrashCan}
                          iconDescription="Delete row"
                          onClick={() => deleteRow(row)}
                          data-cy={`delete-row-${row.rowKey}`}
                        />
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </div>

          {/* Add Row Button */}
          <div style={{ marginTop: "16px", paddingLeft: "16px" }}>
            <Button
              kind="tertiary"
              size="sm"
              renderIcon={Add}
              onClick={addRow}
              data-cy="lab-test-remarks-add-row"
            >
              Add Row
            </Button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LabTestRemarksEdit;
