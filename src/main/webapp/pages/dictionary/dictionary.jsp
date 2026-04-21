<%@ page language="java" contentType="text/html; charset=UTF-8" import="org.openelisglobal.common.action.IActionConstants,
            org.openelisglobal.common.util.Versioning,
            org.openelisglobal.internationalization.MessageUtil" %>
	<%@ page isELIgnored="false" %>
		<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
			<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
				<%@ taglib prefix="c" uri="jakarta.tags.core" %>
					<%@ taglib prefix="ajax" uri="/tags/ajaxtags" %>

						<%! String
							idSeparator=ConfigurationProperties.getInstance().getPropertyValue("default.idSeparator");
							%>
							<%--bugzilla 2061-2063--%>
								<% String recordFrozenDisableEdits="false" ; if
									(request.getAttribute(IActionConstants.RECORD_FROZEN_EDIT_DISABLED_KEY) !=null) {
									recordFrozenDisableEdits=(String)
									request.getAttribute(IActionConstants.RECORD_FROZEN_EDIT_DISABLED_KEY); } String
									previousDisabled="false" ; String nextDisabled="false" ; if
									(request.getAttribute(IActionConstants.PREVIOUS_DISABLED) !=null) {
									previousDisabled=(String) request.getAttribute(IActionConstants.PREVIOUS_DISABLED);
									} if (request.getAttribute(IActionConstants.NEXT_DISABLED) !=null) {
									nextDisabled=(String) request.getAttribute(IActionConstants.NEXT_DISABLED); } String
									saveDisabled=(String) request.getAttribute(IActionConstants.SAVE_DISABLED); if
									("false".equals(saveDisabled)) { if ("true".equals(recordFrozenDisableEdits)) {
									saveDisabled="true" ; } } %>

									<!-- YUP LIBRARY -->
									<script src="https://cdn.jsdelivr.net/npm/yup@1.4.0/dist/yup.min.js"></script>

									<div id="sound"></div>

									<script>
										// Yup validation schema
										const dictionarySchema = yup.object().shape({
											localAbbreviation: yup.string()
												.required('Local abbreviation is required')
												.max(20, 'Max 20 characters')
												.matches(/^[A-Za-z0-9_]+$/, 'Only letters, numbers, and underscores allowed'),
											loincCode: yup.string()
												.required('LOINC code is required')
												.matches(/^\d{5,6}-\d$/, 'LOINC code must be in format XXXXX-X or XXXXXX-X (e.g., 12345-6)'),
											dictEntry: yup.string().required('Dictionary entry is required'),
											isActive: yup.string().required('Active flag is required').matches(/^[YN]$/, 'Must be Y or N')
										});

										async function validateDictionaryForm() {
											const data = {
												localAbbreviation: document.getElementById('localAbbreviation')?.value || '',
												loincCode: document.getElementById('loincCode')?.value || '',
												dictEntry: document.getElementById('dictEntry')?.value || '',
												isActive: document.getElementById('isActive')?.value || ''
											};
											try {
												await dictionarySchema.validate(data, { abortEarly: false });
												clearDictionaryErrors();
												if (containsNewLine(data.dictEntry)) {
													alert('<%=MessageUtil.getMessage("error.dictionary.newlinecharacter")%>');
													return false;
												}
												return true;
											} catch (err) {
												clearDictionaryErrors();
												err.inner.forEach(e => {
													const span = document.getElementById(e.path + 'Error');
													if (span) span.innerText = e.message;
												});
												return false;
											}
										}

										function clearDictionaryErrors() {
											['localAbbreviation', 'loincCode', 'dictEntry', 'isActive'].forEach(f => {
												const span = document.getElementById(f + 'Error');
												if (span) span.innerText = '';
											});
										}

										function containsNewLine(str) {
											return /\n/.test(str);
										}

										// ------------------------------------------------------------------
										// Save / navigation functions (modified to call validation)
										// ------------------------------------------------------------------
										function confirmSaveForwardPopup(direction) {
											var myWin = createSmallConfirmPopup("", null, null);
    <%
												out.println("var message = null;");
        String message = MessageUtil.getMessage("message.popup.confirm.saveandforward");
											out.println("message = '" + message + "';");
        String button1 = MessageUtil.getMessage("label.button.yes");
        String button2 = MessageUtil.getMessage("label.button.no");
        String button3 = MessageUtil.getMessage("label.button.cancel");
        String title = MessageUtil.getMessage("title.popup.confirm.saveandforward");
        String space = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
    %>
    var href = "css/openElisCore.css?";
											var strHTML = "";
											strHTML = '<html><link rel="stylesheet" type="text/css" href="' + href + '" /><head><';
											strHTML += 'SCRIPT LANGUAGE="JavaScript">var tmr = 0;function fnHandleFocus(){if(window.opener.document.hasFocus()){window.focus();clearInterval(tmr);tmr = 0;}else{if(tmr == 0)tmr = setInterval("fnHandleFocus()", 500);}}</SCRIPT';
    strHTML += '><';
    strHTML += 'SCRIPT LANGUAGE="javascript" >'
    strHTML += 'var imp= null; function impor(){imp="norefresh";} ';
    strHTML += ' function fcl(){  if(imp!="norefresh") {  window.opener.reFreshCurWindow(); }}';
    strHTML += '  function goToNextActionSave(){ ';
    strHTML += ' var reqParms = "?direction=next&ID="; ';
    strHTML += ' window.opener.setAction(window.opener.document.getElementById("mainForm"), "UpdateNextPrevious", "yes", reqParms);self.close();} ';
    strHTML += '  function goToPreviousActionSave(){ ';
    strHTML += ' var reqParms = "?direction=previous&ID="; ';
    strHTML += ' window.opener.setAction(window.opener.document.getElementById("mainForm"), "UpdateNextPrevious", "yes", reqParms);self.close();} ';
    strHTML += '  function goToNextActionNoSave(){ ';
    strHTML += ' var reqParms = "?direction=next&ID="; ';
    strHTML += ' window.opener.setAction(window.opener.document.getElementById("mainForm"), "NextPrevious", "no", reqParms);self.close();} ';
    strHTML += '  function goToPreviousActionNoSave(){ ';
    strHTML += ' var reqParms = "?direction=previous&ID="; ';
    strHTML += ' window.opener.setAction(window.opener.document.getElementById("mainForm"), "NextPrevious", "no", reqParms);self.close();} ';
    strHTML += ' setTimeout("impor()",359999);</SCRIPT';
    strHTML += '><title>' + "<%=title%>" + '</title></head>';
    strHTML += '<body onBlur="fnHandleFocus();" onLoad="fnHandleFocus();" ><form name="confirmSaveIt" method="get" action=""><div id="popupBody"><tr><tr><td class="popuplistdata">';
    strHTML += message;
    if (direction == 'next') {
        strHTML += '<br><center><input type="button"  name="save" value="' + "<%=button1%>" + '" onClick="goToNextActionSave();" />';
        strHTML += "<%=space%>";
        strHTML += '<input type="button"  name="save" value="' + "<%=button2%>" + '" onClick="goToNextActionNoSave();"/>';
    } else if (direction == 'previous') {
        strHTML += '<br><center><input type="button"  name="save" value="' + "<%=button1%>" + '" onClick="goToPreviousActionSave();" />';
        strHTML += "<%=space%>";
        strHTML += '<input type="button"  name="save" value="' + "<%=button2%>" + '" onClick="goToPreviousActionNoSave();"/>';
    }
    strHTML += "<%=space%>";
    strHTML += '<input type="button"  name="cancel" value="' + "<%=button3%>" + '" onClick="self.close();" /></center></div>';
    strHTML += '</tr></tr></table></form></body></html>';
    myWin.document.write(strHTML);
    myWin.window.document.close();
    setTimeout('myWin.close()', 360000);
}

function setDirtyFormFields(form) {
    var dirtyFormFields = "";
    for (var i = 0; i < form.elements.length; i++) {
        if (isFormFieldDirty(form.elements[i])) {
            if (i != 0) dirtyFormFields += '<%=idSeparator%>';
            dirtyFormFields += form.elements[i].name;
        }
    }
    form.dirtyFormFields.value = dirtyFormFields;
}

// MODIFIED saveIt – calls Yup validation
async function saveIt(form) {
    if (typeof validateDictionaryForm === 'function') {
        const isValid = await validateDictionaryForm();
        if (!isValid) return;
    } else {
        console.warn("validateDictionaryForm not found – validation skipped");
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

									<!-- ==================== DICTIONARY FORM FIELDS ==================== -->
									<form:hidden path="dirtyFormFields" id="dirtyFormFields" />
									<table>
										<tr>
											<td class="label">
												<spring:message code="dictionary.id" />:
											</td>
											<td>
												<form:input path="id" readonly="true" />
											</td>
										</tr>
										<tr>
											<td class="label">
												<spring:message code="dictionary.dictionarycategory" />:<span
													class="requiredlabel">*</span>
											</td>
											<td>
												<form:select path="selectedDictionaryCategoryId">
													<option></option>
													<form:options items="${form.categories}" itemLabel="description"
														itemValue="id" />
												</form:select>
											</td>
										</tr>
										<tr>
											<td class="label">
												<spring:message code="dictionary.isActive" />:<span
													class="requiredlabel">*</span>
											</td>
											<td width="1">
												<form:input path="isActive" id="isActive" size="1"
													onblur="this.value=this.value.toUpperCase()" />
												<span id="isActiveError" style="color:red"></span>
											</td>
										</tr>
										<tr>
											<td class="label">
												<spring:message code="dictionary.dictEntry" />:<span
													class="requiredlabel">*</span>
											</td>
											<td>
												<form:textarea path="dictEntry" id="dictEntry" cols="50" rows="4" />
												<span id="dictEntryError" style="color:red"></span>
											</td>
										</tr>
										<tr>
											<td class="label">
												<spring:message code="dictionary.localAbbreviation" />:<span
													class="requiredlabel">*</span>
											</td>
											<td>
												<form:input path="localAbbreviation" id="localAbbreviation" size="10"
													onblur="this.value=this.value.toUpperCase()" />
												<span id="localAbbreviationError" style="color:red"></span>
											</td>
										</tr>
										<!-- LOINC code field – required -->
										<tr>
											<td class="label">
												<spring:message code="dictionary.loincCode" />:<span
													class="requiredlabel">*</span>
											</td>
											<td>
												<form:input path="loincCode" id="loincCode" size="20"
													placeholder="e.g., 12345-6" />
												<span id="loincCodeError" style="color:red"></span>
											</td>
										</tr>
										<tr>
											<td>&nbsp;</td>
										</tr>
									</table>

									<!-- ==================== BUTTON BAR ==================== -->
									<center>
										<table border="0" cellpadding="0" cellspacing="0">
											<tbody valign="middle">
												<tr>
													<td>
														<button type="button"
															onclick="if(checkClicked()) { return false; } else { saveIt(document.getElementById('mainForm'));}"
															id="save" <% if
															(Boolean.valueOf(saveDisabled).booleanValue()) { %>
															disabled="disabled" <% } %> >
																<spring:message code="label.button.save" />
														</button>
													</td>
													<td>&nbsp;</td>
													<td>
														<button type="button" onclick="cancelAction();" name="cancel"
															id="cancel">
															<spring:message code="label.button.exit" />
														</button>
													</td>
													<td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>
													<td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>
													<td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>
													<td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>
													<td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>
													<td>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</td>
													<td>
														<button type="button"
															onclick="previousAction(document.getElementById('mainForm'), '');"
															id="previous" <% if
															(Boolean.valueOf(previousDisabled).booleanValue()) { %>
															disabled="disabled" <% } %> >
																<spring:message code="label.button.previous" />
														</button>
													</td>
													<td>&nbsp;</td>
													<td>
														<button type="button"
															onclick="nextAction(document.getElementById('mainForm'), '');"
															id="next" <% if
															(Boolean.valueOf(nextDisabled).booleanValue()) { %>
															disabled="disabled" <% } %> >
																<spring:message code="label.button.next" />
														</button>
													</td>
												</tr>
											</tbody>
										</table>
									</center>