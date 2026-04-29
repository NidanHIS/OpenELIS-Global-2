import React, {
  createContext,
  useState,
  useEffect,
  useContext,
  useRef,
} from "react";
import { useLocation } from "react-router-dom";
import Header from "./Header";
import Footer from "./Footer";
import { Content, Theme } from "@carbon/react";
import UserSessionDetailsContext from "../../UserSessionDetailsContext";
import { getFromOpenElisServer } from "../utils/Utils";
import {
  languages as defaultLanguages,
  buildLanguagesFromConfig,
} from "../../languages";
import { hasPermission, PERMISSIONS } from "../security/rbacPermissions";

export const ConfigurationContext = createContext(null);
export const NotificationContext = createContext(null);

// Side‑nav mode constants – must match what Header expects
const SIDENAV_MODES = {
  CLOSE: "close",
  SHOW: "show",
  LOCK: "lock",
};

export default function Layout(props) {
  const {
    children,
    defaultMode: pageDefaultMode,
    storageKeyPrefix: pageStorageKeyPrefix,
  } = props;
  const location = useLocation();
  const { userSessionDetails } = useContext(UserSessionDetailsContext);
  const [resetConfig, setResetConfig] = useState(false);
  const [configurationProperties, setConfigurationProperties] = useState({});
  const [notificationVisible, setNotificationVisible] = useState(false);
  const [notifications, setNotifications] = useState([]);
  const [supportedLocales, setSupportedLocales] = useState([]);
  const [enabledLanguages, setEnabledLanguages] = useState(defaultLanguages);
  const isFetchingRef = useRef(false);

  // Side‑nav mode state (real, not dummy)
  const [mode, setMode] = useState(SIDENAV_MODES.CLOSE);
  const [isExpanded, setIsExpanded] = useState(false);

  const isStorageContext =
    location.pathname.startsWith("/Storage") ||
    location.pathname.startsWith("/FreezerMonitoring");

  const isAnalyzerContext =
    location.pathname.startsWith("/analyzers") ||
    location.pathname.startsWith("/AnalyzerManagement");
  const isDashboardContext =
    location.pathname === "/" || location.pathname === "/Dashboard";

  const layoutConfig = {
    storageKeyPrefix: pageStorageKeyPrefix
      ? pageStorageKeyPrefix
      : isStorageContext
        ? "storage"
        : isAnalyzerContext
          ? "analyzer"
          : "main",
    defaultMode: pageDefaultMode
      ? pageDefaultMode
      : isStorageContext || isAnalyzerContext
        ? SIDENAV_MODES.LOCK
        : SIDENAV_MODES.CLOSE,
  };

  const addNotification = (notificationBody) => {
    setNotifications([...notifications, notificationBody]);
  };

  const removeNotification = (index) => {
    const newNotifications = [...notifications];
    newNotifications.splice(index, 1);
    setNotifications(newNotifications);
  };

  const fetchConfigurationProperties = (res) => {
    setConfigurationProperties(res);
  };

  const fetchConfig = () => {
    if (isFetchingRef.current) return;
    isFetchingRef.current = true;

    const endpoint = userSessionDetails?.authenticated
      ? "/rest/configuration-properties"
      : "/rest/open-configuration-properties";

    getFromOpenElisServer(endpoint, (res) => {
      fetchConfigurationProperties(res);
      isFetchingRef.current = false;
    });
  };

  // Fetch when authentication changes
  useEffect(() => {
    if (userSessionDetails) {
      fetchConfig();
    }
  }, [userSessionDetails?.authenticated]);

  // Handle manual reload
  useEffect(() => {
    if (resetConfig) {
      fetchConfig();
      setResetConfig(false);
    }
  }, [resetConfig]);

  // Fetch supported locales
  useEffect(() => {
    getFromOpenElisServer("/rest/supportedlocales/active", (response) => {
      if (response && Array.isArray(response)) {
        setSupportedLocales(response);
        const builtLanguages = buildLanguagesFromConfig(response);
        setEnabledLanguages(builtLanguages);
      }
    });
  }, []);

  const isSystemAdmin =
    userSessionDetails &&
    hasPermission(userSessionDetails, PERMISSIONS.SYSTEM_ADMIN);

  // Simple toggle: cycle CLOSE → SHOW → LOCK → CLOSE (or custom logic)
  const toggleSideNav = () => {
    if (mode === SIDENAV_MODES.CLOSE) {
      setMode(SIDENAV_MODES.SHOW);
    } else if (mode === SIDENAV_MODES.SHOW) {
      setMode(SIDENAV_MODES.LOCK);
    } else {
      setMode(SIDENAV_MODES.CLOSE);
    }
  };

  return (
    <ConfigurationContext.Provider
      value={{
        configurationProperties: configurationProperties,
        reloadConfiguration: () => setResetConfig(true),
        supportedLocales: supportedLocales,
        enabledLanguages: enabledLanguages,
      }}
    >
      <NotificationContext.Provider
        value={{
          notificationVisible,
          setNotificationVisible,
          notifications,
          addNotification,
          removeNotification,
        }}
      >
        <div className="d-flex flex-column min-vh-100">
          <Header
            onChangeLanguage={props.onChangeLanguage}
            mode={mode}
            isExpanded={isExpanded}
            toggleSideNav={toggleSideNav}
            setMode={setMode}
            SIDENAV_MODES={SIDENAV_MODES}
            defaultMode={layoutConfig.defaultMode}
            storageKeyPrefix={layoutConfig.storageKeyPrefix}
            showSideNavToggle={isSystemAdmin}
          />

          <div className="d-flex flex-grow-1">
            <Theme theme="white">
              <Content
                data-testid="content-wrapper"
                style={{
                  flex: 1,
                  width: "100%",
                  maxWidth: "100%",
                  marginLeft: 0,
                  padding: isDashboardContext ? 0 : "1rem",
                }}
              >
                {children}
              </Content>
            </Theme>
          </div>

          <Footer />
        </div>
      </NotificationContext.Provider>
    </ConfigurationContext.Provider>
  );
}
