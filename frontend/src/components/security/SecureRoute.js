import React, { useState, useContext, useEffect, useRef } from "react";
import UserSessionDetailsContext from "../../UserSessionDetailsContext";
import { ConfigurationContext } from "../layout/Layout";
import { Route } from "react-router-dom";
import { useIdleTimer } from "react-idle-timer";
import { confirmAlert } from "react-confirm-alert";
import "react-confirm-alert/src/react-confirm-alert.css"; // Import css
import { Loading, Modal } from "@carbon/react/";
import config from "../../config.json";
import { FormattedMessage, useIntl } from "react-intl";
import { navigateTo } from "../utils/Navigation";
import {
  hasAnyPermission,
  hasPermission,
  mapLegacyRoleRequirementsToPermissions,
  PERMISSIONS,
} from "./rbacPermissions";

const idleTimeout = 1000 * 60 * 30; // milliseconds until idle warning will appear
const idleWarningTimeout = 1000 * 60; // milliseconds until logout is automatically processed from idle warning
const idleLogoutTimeout = idleTimeout + idleWarningTimeout;

function SecureRoute(props) {
  const [permissionGranted, setPermissionGranted] = useState(false);
  const [loading, setLoading] = useState(false);
  const [stillThereOpen, setStillThereOpen] = useState(false);

  const intl = useIntl();

  const {
    userSessionDetails,
    errorLoadingSessionDetails,
    isCheckingLogin,
    logout,
  } = useContext(UserSessionDetailsContext);

  const { configurationProperties } = useContext(ConfigurationContext);

  useEffect(() => {
    setLoading(!errorLoadingSessionDetails && isCheckingLogin());
    if (userSessionDetails.authenticated) {
      console.info("Authenticated");
      if (hasRoutePermission(userSessionDetails)) {
        console.info("Access Allowed");
        if (
          configurationProperties.REQUIRE_LAB_UNIT_AT_LOGIN === "true" &&
          !userSessionDetails.loginLabUnit &&
          !hasPermission(userSessionDetails, PERMISSIONS.SYSTEM_ADMIN)
        ) {
          navigateTo("/landing");
        }
      } else {
        const options = {
          title: intl.formatMessage({ id: "accessDenied.title" }),
          message: intl.formatMessage({ id: "accessDenied.message" }),
          buttons: [
            {
              label: intl.formatMessage({ id: "accessDenied.okButton" }),
              onClick: () => {
                navigateTo("/");
              },
            },
          ],
          closeOnClickOutside: false,
          closeOnEscape: false,
        };
        confirmAlert(options);
      }
      setPermissionGranted(hasRoutePermission(userSessionDetails));
    } else if ("authenticated" in userSessionDetails) {
      navigateTo(config.loginRedirect);
    }
  }, [userSessionDetails, errorLoadingSessionDetails]);

  const hasRoutePermission = (userDetails = userSessionDetails) => {
    if (props.permission) {
      return hasPermission(
        userDetails,
        props.permission,
        props.labName || userDetails.loginLabUnit,
      );
    }

    if (props.permissions && props.permissions.length > 0) {
      return hasAnyPermission(
        userDetails,
        props.permissions,
        props.labName || userDetails.loginLabUnit,
      );
    }

    const legacyPermissions = mapLegacyRoleRequirementsToPermissions({
      role: props.role,
      labUnitRole: props.labUnitRole,
    });
    if (legacyPermissions.length === 0) {
      return true;
    }
    return hasAnyPermission(
      userDetails,
      legacyPermissions,
      props.labName || userDetails.loginLabUnit,
    );
  };

  const onIdle = () => {
    setStillThereOpen(false);
    console.debug("idleTimer now idle");
    logout();
  };

  const onActive = () => {
    setStillThereOpen(false);
    console.debug("idleTimer now active");
  };

  const onPrompt = () => {
    setStillThereOpen(true);
    console.debug("idleTimer now prompting");
  };

  const { activate } = useIdleTimer({
    onIdle,
    onActive,
    onPrompt,
    timeout: idleLogoutTimeout,
    promptBeforeIdle: idleWarningTimeout,
    crossTab: true,
    syncTimers: true,
  });

  const handleStillHere = () => {
    activate();
  };

  return (
    <>
      <Modal
        open={stillThereOpen}
        onRequestClose={() => {
          setStillThereOpen(false);
          handleStillHere();
        }}
        modalHeading={intl.formatMessage({ id: "stillThere.title" })}
        passiveModal
      >
        <FormattedMessage id="stillThere.message" />
      </Modal>
      {loading && <Loading />}
      {!loading &&
        !userSessionDetails.authenticated &&
        intl.formatMessage({ id: "notAuthenticated" })}
      {!loading && userSessionDetails.authenticated && permissionGranted && (
        <>{!stillThereOpen && <Route {...props} />}</>
      )}
    </>
  );
}

export default SecureRoute;
