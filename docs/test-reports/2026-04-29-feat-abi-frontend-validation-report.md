# Validation Report: `feat/abi-frontend`

Date: 2026-04-29  
Branch: `feat/abi-frontend`

## Goal

Validate the current branch state, with emphasis on:

- dashboard UI changes
- RBAC-related behavior
- external-order to incoming-order flow
- sample-patient-entry auto-fill behavior for testing
- focused submit validation for the real external-order review path

## Working Tree Snapshot

At validation time, local modifications existed in these files:

- `frontend/src/components/addOrder/Index.js`
- `frontend/src/components/addOrder/SampleType.js`
- `frontend/src/components/home/Dashboard.css`
- `frontend/src/components/home/Dashboard.tsx`
- `frontend/src/components/layout/Layout.js`
- `frontend/src/components/resultPage/SearchResultForm.js`
- `frontend/src/index.css`
- `frontend/src/languages/en.json`
- `src/main/java/org/openelisglobal/dataexchange/externalorders/ExternalOrderRestController.java`
- `src/main/java/org/openelisglobal/dataexchange/externalorders/dto/ExternalOrderRequest.java`
- `src/main/java/org/openelisglobal/dataexchange/externalorders/service/ExternalOrderFormMapperServiceImpl.java`
- `src/main/java/org/openelisglobal/dataexchange/externalorders/service/ExternalOrderValidationServiceImpl.java`
- `src/main/java/org/openelisglobal/dataexchange/externalorders/service/IncomingOrderServiceImpl.java`

## Commands Run

### 1. Frontend production build

Command:

```powershell
npm run build
```

Result: `PASS`

Notes:

- build completed successfully
- warnings remain from existing Carbon source-map issues and repo-wide lint
  noise
- no new build-stopping error was introduced by the validated changes

### 2. Targeted RBAC frontend unit test

Command:

```powershell
$env:CI='true'; npm test -- --runInBand src/components/security/rbacPermissions.test.js
```

Result: `PASS`

Observed output summary:

- test suite passed
- 4 tests passed
- 0 failures

Validated behavior:

- permission contexts are built from session details
- global/all-lab/lab-specific permission checks work
- legacy role mapping is preserved
- any-permission helper works correctly

### 3. Dataexport prerequisite build

Commands:

```powershell
git submodule update --init dataexport
cd dataexport
mvn -q clean install -DskipTests "-Dmaven.test.skip=true"
```

Result: `PASS`

### 4. Backend compile

Command:

```powershell
mvn -q -DskipTests "-Dmaven.test.skip=true" compile
```

Result: `PASS`

Interpretation:

- the Java backend now compiles in this workspace
- the earlier dependency-resolution blocker was an environment prerequisite, not
  a code defect in the validated changes

### 5. Focused local runtime validation

Commands and actions:

```powershell
docker ps
Invoke-RestMethod http://localhost:8080/OpenELIS-Global/rest/external-orders
Invoke-RestMethod http://localhost:8080/OpenELIS-Global/rest/incoming-orders/{externalOrderNumber}/sample-patient-entry-form
Invoke-RestMethod http://localhost:8080/OpenELIS-Global/rest/SamplePatientEntry
Invoke-RestMethod http://localhost:8080/OpenELIS-Global/rest/incoming-orders/{externalOrderNumber}/finalize
docker exec openelisglobal-database psql ...
```

Result: `PASS` after two targeted backend fixes and redeploy

Validated behavior:

- local Docker stack was up and serving the OpenELIS app
- a fresh external order was accepted and returned an accession number
- the generated incoming-order form came back prefilled
- submit succeeded against `/rest/SamplePatientEntry`
- finalization removed the incoming holding order
- the created sample remained persisted in the database

## Code Inspection Findings

### Finding 1: Hardcoded dashboard bootstrap removed

Severity: Resolved

Location:

- `frontend/src/components/home/Dashboard.tsx`

Details:

- dashboard no longer posts a hardcoded test order on load
- it now reads actual incoming orders from the server

### Finding 2: Dormant auto-submit path removed

Severity: Resolved

Location:

- `frontend/src/components/addOrder/Index.js`

Details:

- the unused `autoSubmitIncomingOrder` path was removed from `Index.js`
- current behavior now matches the visible workflow: user opens the form and
  submits intentionally

### Finding 3: External-order defaults still deserve production review

Severity: Medium

Location:

- `ExternalOrderRestController.java`
- `ExternalOrderFormMapperServiceImpl.java`

Details:

- the branch still applies testing-friendly defaults for missing external-order
  values
- this may be exactly what is wanted for the current test flow, but it should be
  confirmed before production merge

Recommendation:

- confirm which defaults are intentionally part of the supported API contract

### Finding 4: Submit path had a nullable checkbox crash

Severity: Resolved

Location:

- `src/main/java/org/openelisglobal/sample/controller/rest/SamplePatientEntryRestController.java`

Details:

- runtime validation showed submit could persist the sample and then fail with a
  500 because `rememberSiteAndRequester` was `null`
- this was fixed by making the controller null-safe with
  `Boolean.TRUE.equals(...)`

### Finding 5: External-order sample dates were emitted in the wrong format

Severity: Resolved

Location:

- `src/main/java/org/openelisglobal/dataexchange/externalorders/service/ExternalOrderFormMapperServiceImpl.java`

Details:

- runtime validation showed `sampleXML` collection dates were being written as
  `yyyy-MM-dd`
- `SamplePatientEntry` validation expects UI-format dates (`MM/dd/yyyy`)
- the mapper now converts provided sample collection dates before building the
  XML payload

## Validation Summary

### Passed

- frontend build
- targeted RBAC test suite
- dataexport submodule initialization and build
- backend compile
- hardcoded dashboard bootstrap removal
- dormant auto-submit removal
- focused runtime validation of external order -> collect/review -> submit
- branch documentation and test-report generation

### Remaining gaps

- this session validated the workflow through API-level replay rather than an
  interactive browser click-through
- full branch-wide regression coverage is still out of scope for this report

## Focused Runtime Evidence

Fresh validation order:

- `externalOrderNumber = abi-validation-20260429-5`
- `patientGuid = 7de4189c-7209-4251-93e1-9157764c0c7d`
- `testGuid = 3578f53d-8e5c-4e16-9dd3-65fe4d1a1f4d`

Observed outputs:

- create response accession number: `DEV01260000000000022`
- form returned same `labNo`
- form returned:
  - `nextVisitDate = 05/01/2026`
  - `providerEmail = external@test.com`
  - `paymentOptionSelection = 1122`
  - `testLocationCode = 1310`
- sample persisted in database:
  - `sample.id = 6`
  - `accession_number = DEV01260000000000022`
