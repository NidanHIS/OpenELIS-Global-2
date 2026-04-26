<%@ page language="java" contentType="text/html; charset=UTF-8" import="org.openelisglobal.common.action.IActionConstants,
            org.openelisglobal.common.util.Versioning,
            org.openelisglobal.internationalization.MessageUtil" %>
  <%@ page isELIgnored="false" %>

    <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
      <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
        <%@ taglib prefix="c" uri="jakarta.tags.core" %>
          <%@ taglib prefix="ajax" uri="/tags/ajaxtags" %>

            <%! String idSeparator=ConfigurationProperties.getInstance().getPropertyValue("default.idSeparator"); %>

              <% String recordFrozenDisableEdits="false" ; if
                (request.getAttribute(IActionConstants.RECORD_FROZEN_EDIT_DISABLED_KEY) !=null) {
                recordFrozenDisableEdits=(String)
                request.getAttribute(IActionConstants.RECORD_FROZEN_EDIT_DISABLED_KEY); } String
                previousDisabled="false" ; String nextDisabled="false" ; if
                (request.getAttribute(IActionConstants.PREVIOUS_DISABLED) !=null) { previousDisabled=(String)
                request.getAttribute(IActionConstants.PREVIOUS_DISABLED); } if
                (request.getAttribute(IActionConstants.NEXT_DISABLED) !=null) { nextDisabled=(String)
                request.getAttribute(IActionConstants.NEXT_DISABLED); } String saveDisabled=(String)
                request.getAttribute(IActionConstants.SAVE_DISABLED); if ("false".equals(saveDisabled)) { if
                ("true".equals(recordFrozenDisableEdits)) { saveDisabled="true" ; } } String
                message=MessageUtil.getMessage("message.popup.confirm.saveandforward"); String
                button1=MessageUtil.getMessage("label.button.yes"); String
                button2=MessageUtil.getMessage("label.button.no"); String
                button3=MessageUtil.getMessage("label.button.cancel"); String
                title=MessageUtil.getMessage("title.popup.confirm.saveandforward"); String
                safeMessage=message.replace("\"", "\\\"");
%>

<script>
  function confirmSaveForwardPopup(direction) {
    var myWin = createSmallConfirmPopup("", null, null);

    var message = " <%=safeMessage%>";
    var href = "css/openElisCore.css?";

    var strHTML = "";
    strHTML += '<html>

      < head > ';
    strHTML += '
      < link rel = "stylesheet" type = "text/css" href = "' + href + '" /> ';
    strHTML += '
      < script >var tmr = 0; function fnHandleFocus() {
        if (window.opener.document.hasFocus()) {
          window.focus(); clearInterval(tmr); tmr = 0;
        } else {
          if (tmr == 0) tmr = setInterval(fnHandleFocus,
            500);
        }
      }</script>
                    ';
                    strHTML += '
                    <script>';
    strHTML += 'var imp=null;function impor(){imp="norefresh";}';
    strHTML += 'function goToNextActionSave(){var reqParms="?direction=next&ID=";window.opener.setAction(window.opener.document.getElementById("mainForm"),"UpdateNextPrevious","yes",reqParms);self.close();}';
    strHTML += 'function goToPreviousActionSave(){var reqParms="?direction=previous&ID=";window.opener.setAction(window.opener.document.getElementById("mainForm"),"UpdateNextPrevious","yes",reqParms);self.close();}';
    strHTML += 'function goToNextActionNoSave(){var reqParms="?direction=next&ID=";window.opener.setAction(window.opener.document.getElementById("mainForm"),"NextPrevious","no",reqParms);self.close();}';
    strHTML += 'function goToPreviousActionNoSave(){var reqParms="?direction=previous&ID=";window.opener.setAction(window.opener.document.getElementById("mainForm"),"NextPrevious","no",reqParms);self.close();}';
    strHTML += 'setTimeout(impor,359999);';
    strHTML += '</script>';
                    strHTML += '<title>
                      <%=title%>
                    </title>
                    </head>';

                    strHTML += '

                    <body onBlur="fnHandleFocus();" onLoad="fnHandleFocus();">';
                      strHTML += '<form>
                        <div id="popupBody">
                          <table>
                            <tr>
                              <td class="popuplistdata">';
                                strHTML += message;

                                if (direction === 'next') {
                                strHTML += '<br>
                                <center>';
                                  strHTML += '<input type="button" value="<%=button1%>"
                                    onclick="goToNextActionSave();" />';
                                  strHTML += '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;';
                                  strHTML += '<input type="button" value="<%=button2%>"
                                    onclick="goToNextActionNoSave();" />';
                                  } else if (direction === 'previous') {
                                  strHTML += '<br>
                                  <center>';
                                    strHTML += '<input type="button" value="<%=button1%>"
                                      onclick="goToPreviousActionSave();" />';
                                    strHTML += '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;';
                                    strHTML += '<input type="button" value="<%=button2%>"
                                      onclick="goToPreviousActionNoSave();" />';
                                    }

                                    strHTML += '&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;';
                                    strHTML += '<input type="button" value="<%=button3%>" onclick="self.close();" />';
                                    strHTML += '</center>
                              </td>
                            </tr>
                          </table>
                        </div>
                      </form>
                    </body>

                </html>';

                myWin.document.write(strHTML);
                myWin.document.close();

                setTimeout(function(){ myWin.close(); }, 360000);
                }

                function setDirtyFormFields(form) {
                var dirtyFormFields = "";
                for (var i = 0; i < form.elements.length; i++) { if (isFormFieldDirty(form.elements[i])) { if
                  (dirtyFormFields.length> 0) {
                  dirtyFormFields += '<%=idSeparator%>';
                    }
                    dirtyFormFields += form.elements[i].name;
                    }
                    }
                    if (form.dirtyFormFields) {
                    form.dirtyFormFields.value = dirtyFormFields;
                    }
                    }

                    async function saveIt(form) {
                    if (typeof validateDictionaryForm === 'function') {
                    const isValid = await validateDictionaryForm();
                    if (!isValid) return;
                    }
                    setDirtyFormFields(form);
                    setAction(form, '', 'yes', '?ID=');
                    }

                    function previousAction(form, ignoreFields) {
                    if (isDirty(form, ignoreFields)) {
                    confirmSaveForwardPopup('previous');
                    } else {
                    setDirtyFormFields(form);
                    navigationAction(form, 'NextPrevious', 'no', '?direction=previous&ID=');
                    }
                    }

                    function nextAction(form, ignoreFields) {
                    if (isDirty(form, ignoreFields)) {
                    confirmSaveForwardPopup('next');
                    } else {
                    setDirtyFormFields(form);
                    navigationAction(form, 'NextPrevious', 'no', '?direction=next&ID=');
                    }
                    }
                    </script>

                    <center>
                      <table border="0" cellpadding="0" cellspacing="0">
                        <tbody valign="middle">
                          <tr>

                            <td>
                              <button type="button"
                                onclick="if(checkClicked()){return false;}else{saveIt(document.getElementById('mainForm'));}"
                                id="save" <% if (Boolean.valueOf(saveDisabled)) { %> disabled="disabled" <% } %>>
                                  <spring:message code="label.button.save" />
                              </button>
                            </td>

                            <td>&nbsp;</td>

                            <td>
                              <button type="button" onclick="cancelAction();" id="cancel">
                                <spring:message code="label.button.exit" />
                              </button>
                            </td>

                            <td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>

                            <td>
                              <button type="button" onclick="previousAction(document.getElementById('mainForm'), '');"
                                id="previous" <% if (Boolean.valueOf(previousDisabled)) { %> disabled="disabled" <% } %>
                                  >
                                  <spring:message code="label.button.previous" />
                              </button>
                            </td>

                            <td>&nbsp;</td>

                            <td>
                              <button type="button" onclick="nextAction(document.getElementById('mainForm'), '');"
                                id="next" <% if (Boolean.valueOf(nextDisabled)) { %> disabled="disabled" <% } %>>
                                  <spring:message code="label.button.next" />
                              </button>
                            </td>

                          </tr>
                        </tbody>
                      </table>
                    </center>