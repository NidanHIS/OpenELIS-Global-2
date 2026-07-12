import React, { useContext, useEffect, useRef, useState } from "react";
import {
  Button,
  Checkbox,
  Grid,
  Column,
  Heading,
  Loading,
  Section,
  InlineNotification,
} from "@carbon/react";
import {
  getFromOpenElisServerV2,
  putToOpenElisServerFullResponse,
} from "../../utils/Utils";
import { NotificationContext } from "../../layout/Layout";
import {
  AlertDialog,
  NotificationKinds,
} from "../../common/CustomNotification";
import PageBreadCrumb from "../../common/PageBreadCrumb";
import UserSessionDetailsContext from "../../../UserSessionDetailsContext";
import { Roles, hasRole } from "../../utils/Utils";

const breadcrumbs = [
  { label: "home.label", link: "/" },
  { label: "breadcrums.admin.managment", link: "/MasterListsPage" },
  {
    label: "Paywall Config",
    link: "/MasterListsPage/nidanPaywallConfig",
  },
];

/**
 * Admin page: configure which OpenMRS visit types bypass the paywall check.
 *
 * Backed by GET/PUT /rest/nidan/paywall-config.
 * Three checkboxes — OPD / IPD / ER.
 * Checked  → visit type is allowed (paywall skipped).
 * Unchecked → paywall is enforced (Odoo check runs).
 */
export default function NidanPaywallConfig() {
  const componentMounted = useRef(false);

  const { notificationVisible, addNotification, setNotificationVisible } =
    useContext(NotificationContext);

  const { userSessionDetails } = useContext(UserSessionDetailsContext);

  const canEdit = hasRole(userSessionDetails, Roles.PAYWALL_ADMIN);

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [fetchError, setFetchError] = useState(null);

  // Config state
  const [allowOpd, setAllowOpd] = useState(false);
  const [allowIpd, setAllowIpd] = useState(true);
  const [allowEr, setAllowEr] = useState(true);

  // ── Load ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    componentMounted.current = true;

    getFromOpenElisServerV2("/rest/nidan/paywall-config")
      .then((data) => {
        if (!componentMounted.current) return;
        if (data) {
          setAllowOpd(!!data.allowOpd);
          setAllowIpd(!!data.allowIpd);
          setAllowEr(!!data.allowEr);
        }
        setLoading(false);
      })
      .catch((err) => {
        if (!componentMounted.current) return;
        setFetchError(
          "Failed to load paywall config: " + (err?.message || "unknown error"),
        );
        setLoading(false);
      });

    return () => {
      componentMounted.current = false;
    };
  }, []);

  // ── Save ──────────────────────────────────────────────────────────────────
  const handleSubmit = (e) => {
    e.preventDefault();
    if (!canEdit) return;
    setSaving(true);

    const body = JSON.stringify({ allowOpd, allowIpd, allowEr });

    putToOpenElisServerFullResponse(
      "/rest/nidan/paywall-config",
      body,
      (res) => {
        if (!componentMounted.current) return;
        if (res.status === 200) {
          addNotification({
            kind: NotificationKinds.success,
            title: "Saved",
            message: "Paywall configuration updated.",
          });
        } else {
          addNotification({
            kind: NotificationKinds.error,
            title: "Error",
            message: "Failed to save config (HTTP " + res.status + ").",
          });
        }
        setNotificationVisible(true);
        setSaving(false);
      },
    );
  };

  // ── Render ────────────────────────────────────────────────────────────────
  return (
    <>
      {notificationVisible && <AlertDialog />}

      <div className="adminPageContent">
        <PageBreadCrumb breadcrumbs={breadcrumbs} />

        <Grid fullWidth>
          <Column lg={16} md={8} sm={4}>
            <Section>
              <Heading>Paywall Configuration</Heading>
            </Section>
          </Column>
        </Grid>

        {loading && <Loading />}

        {fetchError && (
          <InlineNotification
            kind="error"
            title="Load error"
            subtitle={fetchError}
            hideCloseButton
            style={{ marginBottom: "1rem" }}
          />
        )}

        {!loading && !fetchError && (
          <div className="orderLegendBody">
            {!canEdit && (
              <InlineNotification
                kind="warning"
                title="Read-only"
                subtitle="You need the Paywall Administration role to make changes."
                hideCloseButton
                style={{ marginBottom: "1rem" }}
              />
            )}

            <form onSubmit={handleSubmit}>
              <Grid fullWidth>
                <Column lg={8} md={8} sm={4}>
                  <Checkbox
                    id="paywall-allow-opd"
                    labelText="OPD — Outpatient Department"
                    checked={allowOpd}
                    disabled={!canEdit}
                    onChange={(_, { checked }) => setAllowOpd(checked)}
                  />
                  <Checkbox
                    id="paywall-allow-ipd"
                    labelText="IPD — Inpatient Department"
                    checked={allowIpd}
                    disabled={!canEdit}
                    onChange={(_, { checked }) => setAllowIpd(checked)}
                  />
                  <Checkbox
                    id="paywall-allow-er"
                    labelText="ER — Emergency Room"
                    checked={allowEr}
                    disabled={!canEdit}
                    onChange={(_, { checked }) => setAllowEr(checked)}
                  />
                </Column>
              </Grid>

              {canEdit && (
                <div style={{ marginTop: "1.5rem" }}>
                  <Button type="submit" disabled={saving}>
                    {saving ? <Loading small /> : "Save"}
                  </Button>
                </div>
              )}
            </form>
          </div>
        )}
      </div>
    </>
  );
}
