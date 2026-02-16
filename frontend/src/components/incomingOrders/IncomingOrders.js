import React, { useContext, useEffect, useRef, useState } from "react";
import {
  Button,
  DataTable,
  InlineLoading,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
} from "@carbon/react";
import { FormattedMessage, useIntl } from "react-intl";
import {
  getFromOpenElisServer,
  postToOpenElisServerJsonResponse,
} from "../utils/Utils";
import { NotificationContext } from "../layout/Layout";
import { AlertDialog, NotificationKinds } from "../common/CustomNotification";

export default function IncomingOrders() {
  const intl = useIntl();
  const componentMounted = useRef(false);

  const { notificationVisible, setNotificationVisible, addNotification } =
    useContext(NotificationContext);

  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState([]);

  const headers = [
    {
      key: "externalOrderNumber",
      header: intl.formatMessage({ id: "incomingOrders.table.externalOrderNumber" }),
    },
    {
      key: "patientGuid",
      header: intl.formatMessage({ id: "incomingOrders.table.patientGuid" }),
    },
    {
      key: "receivedTimestamp",
      header: intl.formatMessage({ id: "incomingOrders.table.receivedTimestamp" }),
    },
    {
      key: "actions",
      header: intl.formatMessage({ id: "incomingOrders.table.actions" }),
    },
  ];

  const loadRows = () => {
    setLoading(true);
    getFromOpenElisServer("/rest/incoming-orders", (data) => {
      if (!componentMounted.current) {
        return;
      }
      const list = Array.isArray(data) ? data : [];
      const mapped = list.map((item) => ({
        id: String(item.externalOrderNumber || ""),
        externalOrderNumber: item.externalOrderNumber || "",
        patientGuid: item.patientGuid || "",
        receivedTimestamp: item.receivedTimestamp || "",
      }));
      setRows(mapped);
      setLoading(false);
    });
  };

  useEffect(() => {
    componentMounted.current = true;
    loadRows();
    return () => {
      componentMounted.current = false;
    };
  }, []);

  const onCollect = (row) => {
    if (!row) {
      return;
    }

    setLoading(true);
    const externalOrderNumber = row.id || row.externalOrderNumber || "";
    const url =
      "/rest/incoming-orders/" +
      encodeURIComponent(externalOrderNumber) +
      "/collect";

    postToOpenElisServerJsonResponse(url, JSON.stringify({}), (res) => {
      if (!componentMounted.current) {
        return;
      }

      if (res && (res.status === 0 || res.status >= 400)) {
        setNotificationVisible(true);
        addNotification({
          kind: NotificationKinds.error,
          title: intl.formatMessage({ id: "notification.title" }),
          message: res.message || res.error || res.statusText || "Error",
        });
        setLoading(false);
        return;
      }

      setNotificationVisible(true);
      addNotification({
        kind: NotificationKinds.success,
        title: intl.formatMessage({ id: "notification.title" }),
        message: intl.formatMessage({
          id: "incomingOrders.notification.collectSuccess",
        }),
      });

      setRows((prev) => prev.filter((r) => r.id !== row.id));
      setLoading(false);
    });
  };

  return (
    <>
      {notificationVisible === true ? <AlertDialog /> : ""}

      <TableContainer
        title={intl.formatMessage({ id: "incomingOrders.title" })}
        description={intl.formatMessage({ id: "incomingOrders.description" })}
      >
        {loading ? (
          <InlineLoading
            description={intl.formatMessage({ id: "incomingOrders.loading" })}
          />
        ) : null}

        <DataTable rows={rows} headers={headers} isSortable={false}>
          {({ rows, headers, getHeaderProps, getTableProps }) => (
            <Table {...getTableProps()}>
              <TableHead>
                <TableRow>
                  {headers.map((header) => (
                    <TableHeader {...getHeaderProps({ header })}>
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
                        return (
                          <TableCell key={cell.id}>
                            <Button
                              size="sm"
                              kind="primary"
                              disabled={loading}
                              onClick={() => {
                                onCollect(row);
                              }}
                            >
                              <FormattedMessage id="incomingOrders.collect" />
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
      </TableContainer>
    </>
  );
}
