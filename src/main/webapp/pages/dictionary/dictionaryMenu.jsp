<%@ page language="java" contentType="text/html; charset=UTF-8" import="org.openelisglobal.dictionary.valueholder.Dictionary,
		org.openelisglobal.common.action.IActionConstants" %>

	<%@ page isELIgnored="false" %>
		<%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
			<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
				<%@ taglib prefix="c" uri="jakarta.tags.core" %>
					<%@ taglib prefix="fn" uri="jakarta.tags.functions" %>

						<!-- ==================== INLINE VALIDATION STYLES ==================== -->
						<style>
							.dict-modal-overlay {
								display: none;
								position: fixed;
								inset: 0;
								background: rgba(0, 0, 0, 0.55);
								z-index: 1000;
								align-items: center;
								justify-content: center;
							}

							.dict-modal-overlay.active {
								display: flex;
							}

							.dict-modal {
								background: #fff;
								border-radius: 4px;
								width: 500px;
								max-width: 96vw;
								max-height: 90vh;
								overflow-y: auto;
								box-shadow: 0 8px 32px rgba(0, 0, 0, 0.22);
								display: flex;
								flex-direction: column;
							}

							.dict-modal-header {
								padding: 1.1rem 1.4rem 0.7rem;
								border-bottom: 1px solid #e0e0e0;
								display: flex;
								justify-content: space-between;
								align-items: center;
							}

							.dict-modal-header h2 {
								margin: 0;
								font-size: 1.15rem;
								font-weight: 600;
							}

							.dict-modal-close {
								background: none;
								border: none;
								font-size: 1.3rem;
								cursor: pointer;
								color: #525252;
								line-height: 1;
								padding: 0 0.2rem;
							}

							.dict-modal-body {
								padding: 1.2rem 1.4rem;
								flex: 1;
							}

							.dict-field-group {
								margin-bottom: 1rem;
							}

							.dict-field-group label {
								display: block;
								font-size: 0.85rem;
								font-weight: 500;
								margin-bottom: 0.3rem;
								color: #161616;
							}

							.dict-field-group label .required-star {
								color: #da1e28;
								margin-left: 2px;
							}

							.dict-field-group input[type="text"],
							.dict-field-group textarea,
							.dict-field-group select {
								width: 100%;
								padding: 0.5rem 0.65rem;
								border: 1px solid #8d8d8d;
								border-radius: 2px;
								font-size: 0.9rem;
								box-sizing: border-box;
								background: #f4f4f4;
								transition: border-color 0.15s;
								outline: none;
							}

							.dict-field-group input[type="text"]:focus,
							.dict-field-group textarea:focus,
							.dict-field-group select:focus {
								border-color: #0f62fe;
								background: #fff;
							}

							/* ── ERROR STATE ── */
							.dict-field-group input.field-error,
							.dict-field-group textarea.field-error,
							.dict-field-group select.field-error {
								border-color: #da1e28 !important;
								background: #fff8f8;
							}

							.dict-inline-error {
								display: none;
								color: #da1e28;
								font-size: 0.78rem;
								margin-top: 0.28rem;
								font-weight: 500;
							}

							.dict-inline-error.visible {
								display: block;
							}

							/* ── FOOTER BUTTONS ── */
							.dict-modal-footer {
								display: flex;
								border-top: 1px solid #e0e0e0;
							}

							.dict-modal-footer button {
								flex: 1;
								padding: 1rem;
								border: none;
								font-size: 0.95rem;
								font-weight: 600;
								cursor: pointer;
								transition: background 0.15s;
							}

							.dict-btn-cancel {
								background: #393939;
								color: #fff;
							}

							.dict-btn-cancel:hover {
								background: #262626;
							}

							.dict-btn-add {
								background: #0f62fe;
								color: #fff;
							}

							.dict-btn-add:hover {
								background: #0353e9;
							}

							/* ── OPEN MODAL BUTTON ── */
							.open-add-dict-btn {
								margin-bottom: 0.75rem;
								padding: 0.5rem 1.2rem;
								background: #0f62fe;
								color: #fff;
								border: none;
								border-radius: 2px;
								font-size: 0.9rem;
								font-weight: 600;
								cursor: pointer;
							}

							.open-add-dict-btn:hover {
								background: #0353e9;
							}
						</style>

						<!-- ==================== OPEN MODAL BUTTON ==================== -->
						<button type="button" class="open-add-dict-btn" onclick="openAddDictModal()">
							+ Add Dictionary
						</button>

						<!-- ==================== ADD DICTIONARY MODAL ==================== -->
						<div class="dict-modal-overlay" id="addDictModalOverlay" role="dialog" aria-modal="true"
							aria-labelledby="addDictModalTitle">
							<div class="dict-modal">

								<!-- Header -->
								<div class="dict-modal-header">
									<h2 id="addDictModalTitle">Add Dictionary</h2>
									<button type="button" class="dict-modal-close" onclick="closeAddDictModal()"
										title="Close">&times;</button>
								</div>

								<!-- Body -->
								<div class="dict-modal-body">

									<!-- Dictionary Number (read-only / auto) -->
									<div class="dict-field-group">
										<label for="addDictNumber">Dictionary Number</label>
										<input type="text" id="addDictNumber" placeholder="Dictionary Number"
											readonly />
									</div>

									<!-- Dictionary Category -->
									<div class="dict-field-group">
										<label for="addDictCategory">Dictionary Category</label>
										<select id="addDictCategory">
											<option value=""></option>
											<c:forEach items="${form.categories}" var="cat">
												<option value="${cat.id}">
													<c:out value="${cat.description}" />
												</option>
											</c:forEach>
										</select>
									</div>

									<!-- Dictionary Entry -->
									<div class="dict-field-group">
										<label for="addDictEntry">Dictionary Entry</label>
										<textarea id="addDictEntry" rows="3" placeholder="Dictionary Entry"></textarea>
									</div>

									<!-- Is Active -->
									<div class="dict-field-group">
										<label for="addDictIsActive">Is Active</label>
										<select id="addDictIsActive">
											<option value="Y">Y</option>
											<option value="N">N</option>
										</select>
									</div>

									<!-- Local Abbreviation — REQUIRED -->
									<div class="dict-field-group">
										<label for="addDictLocalAbbr">
											Local Abbreviation<span class="required-star">*</span>
										</label>
										<input type="text" id="addDictLocalAbbr" placeholder="Local Abbreviation"
											maxlength="20"
											oninput="clearFieldError('addDictLocalAbbr', 'addDictLocalAbbrError')" />
										<span class="dict-inline-error" id="addDictLocalAbbrError">
											Local Abbreviation is required.
										</span>
									</div>

									<!-- LOINC Code — REQUIRED -->
									<div class="dict-field-group">
										<label for="addDictLoincCode">
											LOINC Code<span class="required-star">*</span>
										</label>
										<input type="text" id="addDictLoincCode" placeholder="e.g., 12345-6"
											oninput="clearFieldError('addDictLoincCode', 'addDictLoincCodeError')" />
										<span class="dict-inline-error" id="addDictLoincCodeError">
											LOINC Code is required.
										</span>
									</div>

								</div><!-- /body -->

								<!-- Footer -->
								<div class="dict-modal-footer">
									<button type="button" class="dict-btn-cancel"
										onclick="closeAddDictModal()">Cancel</button>
									<button type="button" class="dict-btn-add"
										onclick="submitAddDictionary()">Add</button>
								</div>

							</div><!-- /dict-modal -->
						</div><!-- /overlay -->

						<!-- ==================== MODAL JAVASCRIPT ==================== -->
						<script>
							/* ---------- open / close ---------- */
							function openAddDictModal() {
								document.getElementById('addDictModalOverlay').classList.add('active');
								resetAddDictModal();
							}

							function closeAddDictModal() {
								document.getElementById('addDictModalOverlay').classList.remove('active');
							}

							/* Close on overlay click (outside modal box) */
							document.getElementById('addDictModalOverlay').addEventListener('click', function (e) {
								if (e.target === this) closeAddDictModal();
							});

							/* ---------- reset ---------- */
							function resetAddDictModal() {
								['addDictNumber', 'addDictEntry', 'addDictLocalAbbr', 'addDictLoincCode'].forEach(function (id) {
									var el = document.getElementById(id);
									if (el) el.value = '';
								});
								var cat = document.getElementById('addDictCategory');
								if (cat) cat.selectedIndex = 0;
								var active = document.getElementById('addDictIsActive');
								if (active) active.value = 'Y';
								clearAllFieldErrors();
							}

							/* ---------- inline error helpers ---------- */
							function showFieldError(inputId, errorId) {
								var input = document.getElementById(inputId);
								var error = document.getElementById(errorId);
								if (input) input.classList.add('field-error');
								if (error) error.classList.add('visible');
							}

							function clearFieldError(inputId, errorId) {
								var input = document.getElementById(inputId);
								var error = document.getElementById(errorId);
								if (input) input.classList.remove('field-error');
								if (error) error.classList.remove('visible');
							}

							function clearAllFieldErrors() {
								clearFieldError('addDictLocalAbbr', 'addDictLocalAbbrError');
								clearFieldError('addDictLoincCode', 'addDictLoincCodeError');
							}

							/* ---------- validation + submit ---------- */
							function submitAddDictionary() {
								clearAllFieldErrors();

								var localAbbr = (document.getElementById('addDictLocalAbbr').value || '').trim();
								var loincCode = (document.getElementById('addDictLoincCode').value || '').trim();
								var hasError = false;

								/* Local Abbreviation — required */
								if (!localAbbr) {
									showFieldError('addDictLocalAbbr', 'addDictLocalAbbrError');
									hasError = true;
								}

								/* LOINC Code — required */
								if (!loincCode) {
									showFieldError('addDictLoincCode', 'addDictLoincCodeError');
									hasError = true;
								}

								if (hasError) return; /* stop here — errors shown inline */

								/* ---- all valid: build payload and submit ---- */
								var payload = {
									dictionaryCategory: document.getElementById('addDictCategory').value,
									dictEntry: document.getElementById('addDictEntry').value,
									isActive: document.getElementById('addDictIsActive').value,
									localAbbreviation: localAbbr,
									loincCode: loincCode
								};

								/*
								 * Replace the block below with your real save call, e.g.
								 *   fetch('/rest/dictionary', { method:'POST', body: JSON.stringify(payload), ... })
								 *   .then(...)
								 *
								 * For now we log and close so you can wire it up.
								 */
								console.log('Saving dictionary entry:', payload);
								closeAddDictModal();

								/* Optionally refresh the list here */
								/* location.reload(); */
							}
						</script>

						<!-- ==================== DICTIONARY LIST TABLE ==================== -->
						<table width="100%" border="2">
							<tr>
								<th>
									<spring:message code="label.form.select" />
								</th>
								<th>
									<spring:message code="dictionary.dictionarycategory" />
								</th>
								<th>
									<spring:message code="dictionary.dictEntry" />
								</th>
								<th>
									<spring:message code="dictionary.localAbbreviation" />
								</th>
								<th>
									<spring:message code="dictionary.isActive" />
								</th>
							</tr>
							<form:form name="${form.formName}" action="${form.formAction}" modelAttribute="form"
								onSubmit="return submitForm(this);" method="${form.formMethod}" id="menuForm">
								<c:forEach items="${form.menuList}" var="dict" varStatus="iter">
									<tr>
										<td class="textcontent">
											<form:checkbox path="selectedIDs" onclick="output()" value="${dict.id}" />
										</td>
										<td class="textcontent">
											<c:out value="${dict.dictionaryCategory.categoryName}" />&nbsp;
										</td>
										<td class="textcontent">
											<c:out value="${dict.dictEntry}" />&nbsp;
										</td>
										<td class="textcontent">
											<c:out value="${fn:substring(dict.localAbbreviation, 0, 10)}" />&nbsp;
										</td>
										<td class="textcontent">
											<c:out value="${dict.isActive}" />
										</td>
									</tr>
								</c:forEach>
							</form:form>
						</table>