import * as Yup from "yup";
import CreatePatientValidationSchema from "./CreatePatientValidationShema";

const OrderEntryValidationSchema = Yup.object().shape({
  sampleXML: Yup.string().required("Sample is required"),
  patientProperties: CreatePatientValidationSchema,
  sampleOrderItems: Yup.object()
    .shape({
      labNo: Yup.string().required("Sample Lab Number is required"),
      referringSiteName: Yup.string(),
      referringSiteId: Yup.string(),
      providerLastName: Yup.string().required(
        "Requester Last Name is required",
      ),
      providerFirstName: Yup.string().required(
        "Requester First Name is required",
      ),
      providerEmail: Yup.string().email("Invalid Email"),
      // Optional. Accepts 6-digit display (DDxxxx), 10-digit stored (YYMMDDxxxx),
      // or alphanumeric manual entry. Max 20 chars. No special chars.
      sampleNumber: Yup.string()
        .nullable()
        .max(20, "Sample number max 20 characters")
        .matches(
          /^[A-Za-z0-9]{0,20}$/,
          "Sample number: letters and digits only, no spaces or special characters",
        ),
      sampleNumberType: Yup.string().nullable().oneOf(["AUTO", "MANUAL", null, ""]),
    })
    .test("referringSiteName", "Referring Site is required", function (value) {
      const { referringSiteName, referringSiteId } = value || {};
      return !!referringSiteName || !!referringSiteId;
    }),
});

export default OrderEntryValidationSchema;
