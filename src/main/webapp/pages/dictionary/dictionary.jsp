<%@ page language="java" contentType="text/html; charset=UTF-8" import="org.openelisglobal.common.action.IActionConstants, 
			org.openelisglobal.internationalization.MessageUtil" %>

	<%@ page isELIgnored="false" %>
		<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
			<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
				<%@ taglib prefix="c" uri="jakarta.tags.core" %>

					<%-- ADD YUP LIBRARY --%>
						<script src="https://cdn.jsdelivr.net/npm/yup@1.4.0/dist/yup.min.js"></script>

						<div id="sound"></div>

						<script>
							// Yup schema
							const dictionarySchema = yup.object().shape({
								localAbbreviation: yup.string()
									.required('Local abbreviation is required')
									.max(20, 'Max 20 characters')
									.matches(/^[A-Za-z0-9_]+$/, 'Only letters, numbers, and underscores allowed'),
								loincCode: yup.string()
									.nullable()
									.matches(/^(\d{5,6}-\d)$|^$/, 'LOINC code must be in format XXXXX-X (e.g., 12345-6)'),
								dictEntry: yup.string().required('Dictionary entry is required'),
								isActive: yup.string().required('Active flag is required').matches(/^[YN]$/, 'Must be Y or N')
							});

							async function validateDictionaryForm() {
								const data = {
									localAbbreviation: document.getElementById('localAbbreviation')?.value || '',
									loincCode: document.getElementById('loincCode')?.value || '',
									dictEntry: document.getElementById('dictEntry').value,
									isActive: document.getElementById('isActive').value
								};

								try {
									await dictionarySchema.validate(data, { abortEarly: false });
									clearDictionaryErrors();

									// Also check for newline characters (original requirement)
									if (containsNewLine(data.dictEntry)) {
										alert('<%=MessageUtil.getMessage("error.dictionary.newlinecharacter")%>');
										return false;
									}
									return true;
								} catch (err) {
									clearDictionaryErrors();
									if (err.inner) {
										err.inner.forEach(e => {
											const span = document.getElementById(e.path + 'Error');
											if (span) span.innerText = e.message;
										});
									}
									return false;
								}
							}

							function clearDictionaryErrors() {
								const fields = ['localAbbreviation', 'loincCode', 'dictEntry', 'isActive'];
								fields.forEach(f => {
									const span = document.getElementById(f + 'Error');
									if (span) span.innerText = '';
								});
							}

							function containsNewLine(str) {
								return /\n/.test(str);
							}

							// Preserve original validateForm (if called by legacy code)
							function validateForm(form) {
								return validateDictionaryForm();
							}
						</script>

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
									<spring:message code="dictionary.isActive" />:<span class="requiredlabel">*</span>
								</td>
								<td width="1">
									<form:input path="isActive" id="isActive" size="1"
										onblur="this.value=this.value.toUpperCase()" />
									<span id="isActiveError" style="color:red"></span>
								</td>
							</tr>
							<tr>
								<td class="label">
									<spring:message code="dictionary.dictEntry" />:<span class="requiredlabel">*</span>
								</td>
								<td>
									<form:textarea path="dictEntry" id="dictEntry" cols="50" rows="4" />
									<span id="dictEntryError" style="color:red"></span>
								</td>
							</tr>
							<%--bugzilla 1847--%>
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

								<%-- NEW LOINC CODE FIELD --%>
									<tr>
										<td class="label">
											<spring:message code="dictionary.loincCode" />:
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