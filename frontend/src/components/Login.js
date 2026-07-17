import React, { useContext, useEffect, useState, createRef } from "react";
import config from "../config.json";
import "./Style.css";
import qs from "qs";
import { FormattedMessage, injectIntl } from "react-intl";
import { HardwareSecurityModule } from "@carbon/icons-react";
import {
  Form,
  Section,
  Heading,
  FormLabel,
  TextInput,
  Button,
  Stack,
  Loading,
} from "@carbon/react";
import { Formik } from "formik";
import { AlertDialog, NotificationKinds } from "./common/CustomNotification";
import UserSessionDetailsContext from "../UserSessionDetailsContext";
import { ConfigurationContext, NotificationContext } from "./layout/Layout";
import { navigateTo } from "./utils/Navigation";
import { getBranding } from "./utils/BrandingUtils";
import { getLoginSiteInfoStyles } from "./loginBrandingStyles";

function Login(props) {
  const { notificationVisible, addNotification, setNotificationVisible } =
    useContext(NotificationContext);
  const { configurationProperties } = useContext(ConfigurationContext);

  const { userSessionDetails, refresh } = useContext(UserSessionDetailsContext);
  const [submitting, setSubmitting] = useState(false);
  const [samlRedirectInitiated, setSamlRedirectInitiated] = useState(false);
  const [loginLogoUrl, setLoginLogoUrl] = useState(null);
  const [logoVersion, setLogoVersion] = useState(0); // Version counter for cache-busting
  const [brandingConfig, setBrandingConfig] = useState({});
  const firstInput = createRef();

  // Auto-redirect to SAML if configured to bypass login page
  useEffect(() => {
    if (
      configurationProperties?.useSaml === "true" &&
      configurationProperties?.useSamlLoginPage === "false" &&
      !samlRedirectInitiated &&
      !userSessionDetails.authenticated
    ) {
      // Mark as initiated to prevent multiple redirects
      setSamlRedirectInitiated(true);

      // Use full-page redirect instead of popup to avoid popup blockers
      // Add 'redirect=true' parameter to tell backend to redirect to dashboard after auth
      window.location.href =
        config.serverBaseUrl + "/LoginPage?useSAML=true&redirect=true";
    }
  }, [
    configurationProperties,
    samlRedirectInitiated,
    userSessionDetails.authenticated,
  ]);

  useEffect(() => {
    firstInput?.current?.focus();
  }, []);

  // Load branding configuration for login logo
  // Colors are handled by App.js
  useEffect(() => {
    getBranding((response) => {
      if (response) {
        setBrandingConfig(response);
        // Check useHeaderLogoForLogin flag
        if (response.useHeaderLogoForLogin && response.headerLogoUrl) {
          setLoginLogoUrl(response.headerLogoUrl);
          setLogoVersion((prev) => prev + 1);
        } else if (response.loginLogoUrl) {
          setLoginLogoUrl(response.loginLogoUrl);
          setLogoVersion((prev) => prev + 1);
        }
      }
    });
  }, []);

  useEffect(() => {
    if (userSessionDetails.authenticated) {
      navigateTo("/");
    }
  }, [userSessionDetails]);

  const checkLogin = () => {
    refresh();
  };

  const doLogin = (data) => {
    setSubmitting(true);
    fetch(config.serverBaseUrl + "/ValidateLogin?apiCall=true", {
      //includes the browser sessionId in the Header for Authentication on the backend server
      credentials: "include",
      method: "POST",
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      body: qs.stringify(data),
    })
      .then(async (response) => {
        setSubmitting(false);
        // get json response here
        let data = await response.json();
        if (response.status === 200) {
          navigateTo("/");
        } else {
          addNotification({
            title: props.intl.formatMessage({
              id: "notification.title",
            }),
            message: props.intl.formatMessage({
              id: data.error,
            }),
            kind: NotificationKinds.error,
          });
          setNotificationVisible(true);
        }
      })
      .catch((error) => {
        setSubmitting(false);
        console.error(error);
        if (error instanceof SyntaxError) {
          addNotification({
            title: props.intl.formatMessage({
              id: "notification.title",
            }),
            message: props.intl.formatMessage({
              id: "notification.login.syntax.error",
            }),
            kind: NotificationKinds.error,
          });
          setNotificationVisible(true);
        } else {
          addNotification({
            title: props.intl.formatMessage({
              id: "notification.title",
            }),
            message: props.intl.formatMessage({
              id: "notification.login.generic.error",
            }),
            kind: NotificationKinds.error,
          });
          setNotificationVisible(true);
        }
      });
  };

  const renderOauthButtons = () => {
    return (
      <span id="oauth-buttons">
        {configurationProperties?.oauthUrls?.map((url) => (
          <Button
            key={url.key}
            type="button"
            renderIcon={HardwareSecurityModule}
            onClick={() => {
              window.location.href = config.serverBaseUrl + "/" + url.value;
            }}
          >
            <FormattedMessage id="label.button.login.sso" />
          </Button>
        ))}
      </span>
    );
  };

  // IMPORTANT: use process.env.PUBLIC_URL prefix so the path resolves
  // correctly at /openelis/login regardless of subpath deployment.
  const defaultLogoSrc = `${process.env.PUBLIC_URL}/images/openelis_logo_full.png`;
  const logoSrc = loginLogoUrl
    ? `${config.serverBaseUrl}${loginLogoUrl}?v=${logoVersion}`
    : defaultLogoSrc;

  const { siteNameStyle, additionalSiteInfoStyle, hardcodedLabelStyle } =
    getLoginSiteInfoStyles(brandingConfig);

  return (
    <>
      <div
        data-cy="login-Page-Content"
        className="loginPageContent oe-loginPageContent"
      >
        {notificationVisible === true ? <AlertDialog /> : ""}
        {/* Single flex column — everything shares the same center axis */}
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            paddingTop: "3rem",
            paddingBottom: "3rem",
          }}
        >
          {/* Logo */}
          <div
            style={{
              width: "100%",
              maxWidth: "400px",
              marginBottom: "1rem",
              textAlign: "center",
            }}
          >
            <picture>
              <img
                src={logoSrc}
                alt="fullsize logo"
                style={{
                  objectFit: "contain",
                  width: "100%",
                  height: "auto",
                  display: "block",
                  margin: "0 auto",
                }}
                onError={(e) => {
                  // Guard against infinite loop: only fall back if not already
                  // showing the default.
                  if (
                    e.target.src !==
                    window.location.origin + defaultLogoSrc
                  ) {
                    e.target.src = defaultLogoSrc;
                  }
                }}
              />
            </picture>
          </div>

          {/* Site info */}
          <div
            style={{
              width: "100%",
              maxWidth: "400px",
              textAlign: "center",
              marginBottom: "1.5rem",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              boxSizing: "border-box",
            }}
          >
            {configurationProperties?.SiteName && (
              <div style={siteNameStyle}>
                <strong>{configurationProperties.SiteName}</strong>
              </div>
            )}
            {configurationProperties?.ADDITIONAL_SITE_INFO && (
              <div style={additionalSiteInfoStyle}>
                {configurationProperties.ADDITIONAL_SITE_INFO}
              </div>
            )}
            <div style={hardcodedLabelStyle}>Laboratory Information System</div>
          </div>

          {/* Login form */}
          <div style={{ width: "100%", maxWidth: "400px" }}>
            <Section>
              {samlRedirectInitiated ? (
                <Stack gap={5}>
                  <FormLabel>
                    <Heading>
                      <FormattedMessage id="login.title" />
                    </Heading>
                  </FormLabel>
                  <div style={{ textAlign: "center", padding: "2rem" }}>
                    <Loading
                      description={props.intl.formatMessage({
                        id: "login.redirecting.sso",
                      })}
                      withOverlay={false}
                    />
                    <p style={{ marginTop: "1rem" }}>
                      <FormattedMessage id="login.redirecting.sso" />
                    </p>
                  </div>
                </Stack>
              ) : (
                <Formik
                  initialValues={{
                    username: "",
                    password: "",
                  }}
                  onSubmit={(values) => {
                    doLogin(values);
                  }}
                >
                  {({ isValid, handleChange, handleSubmit }) => (
                    <Form onSubmit={handleSubmit} onChange={handleChange}>
                      <Stack gap={5}>
                        <FormLabel>
                          <Heading>
                            <FormattedMessage id="login.title" />
                          </Heading>
                        </FormLabel>
                        {configurationProperties?.useFormLogin == "true" && (
                          <>
                            <TextInput
                              id="loginName"
                              invalidText={props.intl.formatMessage({
                                id: "login.msg.username.missing",
                              })}
                              labelText={props.intl.formatMessage({
                                id: "login.msg.username",
                              })}
                              hideLabel={true}
                              placeholder={props.intl.formatMessage({
                                id: "login.msg.username",
                              })}
                              autoComplete="off"
                              ref={firstInput}
                            />
                            <TextInput.PasswordInput
                              id="password"
                              invalidText={props.intl.formatMessage({
                                id: "login.msg.password.missing",
                              })}
                              labelText={props.intl.formatMessage({
                                id: "login.msg.password",
                              })}
                              hideLabel={true}
                              placeholder={props.intl.formatMessage({
                                id: "login.msg.password",
                              })}
                            />
                            <div
                              style={{
                                display: "flex",
                                width: "100%",
                                gap: "1rem",
                              }}
                            >
                              <Button
                                type="submit"
                                disabled={!isValid}
                                data-cy="loginButton"
                                style={{ flex: 1 }}
                              >
                                <FormattedMessage id="label.button.login" />
                                <Loading
                                  small={true}
                                  withOverlay={false}
                                  className={submitting ? "show" : "hidden"}
                                />
                              </Button>

                              <Button
                                data-cy="changePassword"
                                type="button"
                                onClick={() => {
                                  navigateTo("/ChangePasswordLogin");
                                }}
                                style={{ flex: 1 }}
                              >
                                <FormattedMessage id="label.button.changepassword" />
                              </Button>
                            </div>
                          </>
                        )}
                        {configurationProperties?.useSaml == "true" &&
                          configurationProperties?.useSamlLoginPage !==
                            "false" && (
                            <Button
                              type="button"
                              renderIcon={HardwareSecurityModule}
                              onClick={() => {
                                // Use full-page redirect instead of popup to avoid popup blockers
                                window.location.href =
                                  config.serverBaseUrl +
                                  "/LoginPage?useSAML=true&redirect=true";
                              }}
                            >
                              <FormattedMessage id="label.button.login.sso" />
                            </Button>
                          )}
                        {configurationProperties?.useOauth == "true" &&
                          renderOauthButtons()}
                      </Stack>
                    </Form>
                  )}
                </Formik>
              )}
            </Section>
          </div>
        </div>
      </div>
    </>
  );
}

export default injectIntl(Login);
