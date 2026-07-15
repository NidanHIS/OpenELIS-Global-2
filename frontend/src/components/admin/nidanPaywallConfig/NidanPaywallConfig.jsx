import React, { useContext, useEffect, useRef, useState } from "react";
import {
  Button,
  Grid,
  Column,
  Heading,
  Loading,
  Section,
  InlineNotification,
  Toggle,
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
  const [configMap, setConfigMap] = useState({});

  // ── Load ──────────────────────────────────────────────────────────────────
  useEffect(() => {
    componentMounted.current = true;

    // Only fetch if the user actually has the paywall admin role.
    // The route guard in Admin.js redirects non-admins away, but this is a
    // second-line defence in case the component is rendered directly.
    if (!canEdit) {
      setLoading(false);
      return () => {
        componentMounted.current = false;
      };
    }

    getFromOpenElisServerV2("/rest/nidan/paywall-config")
      .then((data) => {
        if (!componentMounted.current) return;
        if (data) {
          setConfigMap(data || {});
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

    putToOpenElisServerFullResponse(
      "/rest/nidan/paywall-config",
      JSON.stringify(configMap),
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
                  {Object.entries(configMap).map(([visitType, allowed]) => (
                    <div key={visitType} style={{ marginBottom: "1rem" }}>
                      <Toggle
                        id={`paywall-allow-${visitType.replace(/\s+/g, "-").toLowerCase()}`}
                        labelText={visitType}
                        labelA="Enforce"
                        labelB="Bypass"
                        toggled={allowed}
                        disabled={!canEdit}
                        onToggle={(checked) => {
                          setConfigMap((prev) => ({
                            ...prev,
                            [visitType]: checked,
                          }));
                        }}
                      />
                    </div>
                  ))}
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
