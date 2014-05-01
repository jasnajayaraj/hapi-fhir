package ca.uhn.fhir.rest.server;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicNameValuePair;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.hamcrest.core.IsNot;
import org.hamcrest.core.StringContains;
import org.hamcrest.core.StringEndsWith;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.Bundle;
import ca.uhn.fhir.model.api.BundleEntry;
import ca.uhn.fhir.model.api.IResource;
import ca.uhn.fhir.model.api.PathSpecification;
import ca.uhn.fhir.model.api.ResourceMetadataKeyEnum;
import ca.uhn.fhir.model.dstu.composite.CodingDt;
import ca.uhn.fhir.model.dstu.composite.HumanNameDt;
import ca.uhn.fhir.model.dstu.composite.IdentifierDt;
import ca.uhn.fhir.model.dstu.resource.AdverseReaction;
import ca.uhn.fhir.model.dstu.resource.Conformance;
import ca.uhn.fhir.model.dstu.resource.DiagnosticOrder;
import ca.uhn.fhir.model.dstu.resource.DiagnosticReport;
import ca.uhn.fhir.model.dstu.resource.OperationOutcome;
import ca.uhn.fhir.model.dstu.resource.Patient;
import ca.uhn.fhir.model.dstu.valueset.IdentifierUseEnum;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.model.primitive.InstantDt;
import ca.uhn.fhir.model.primitive.StringDt;
import ca.uhn.fhir.model.primitive.UriDt;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.rest.annotation.Create;
import ca.uhn.fhir.rest.annotation.Delete;
import ca.uhn.fhir.rest.annotation.History;
import ca.uhn.fhir.rest.annotation.IdParam;
import ca.uhn.fhir.rest.annotation.IncludeParam;
import ca.uhn.fhir.rest.annotation.OptionalParam;
import ca.uhn.fhir.rest.annotation.Read;
import ca.uhn.fhir.rest.annotation.RequiredParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
import ca.uhn.fhir.rest.annotation.Search;
import ca.uhn.fhir.rest.annotation.Update;
import ca.uhn.fhir.rest.annotation.Validate;
import ca.uhn.fhir.rest.annotation.VersionIdParam;
import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.param.CodingListParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.QualifiedDateParam;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.exceptions.UnprocessableEntityException;
import ca.uhn.fhir.rest.server.provider.ServerProfileProvider;
import ca.uhn.fhir.testutil.RandomServerPortProvider;
import ca.uhn.fhir.util.ExtensionConstants;

/**
 * Created by dsotnikov on 2/25/2014.
 */
public class ResfulServerMethodTest {

	private static CloseableHttpClient ourClient;
	private static FhirContext ourCtx;
	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(ResfulServerMethodTest.class);
	private static int ourPort;
	private static Server ourServer;

	@Test
	public void test404IsPropagatedCorrectly() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/DiagnosticReport?throw404=true");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(404, status.getStatusLine().getStatusCode());
		assertThat(responseContent, StringContains.containsString("AAAABBBB"));
	}

	@Test
	public void testCreate() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setValue("001");
		patient.addIdentifier().setValue("002");

		HttpPost httpPost = new HttpPost("http://localhost:" + ourPort + "/Patient");
		httpPost.setEntity(new StringEntity(new FhirContext().newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPost);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(201, status.getStatusLine().getStatusCode());
		assertEquals("http://localhost:" + ourPort + "/Patient/001/_history/002", status.getFirstHeader("Location").getValue());

	}

	@Test
	public void testCreateWithUnprocessableEntity() throws Exception {

		DiagnosticReport report = new DiagnosticReport();
		report.getIdentifier().setValue("001");

		HttpPost httpPost = new HttpPost("http://localhost:" + ourPort + "/DiagnosticReport");
		httpPost.setEntity(new StringEntity(new FhirContext().newXmlParser().encodeResourceToString(report), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPost);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(422, status.getStatusLine().getStatusCode());

		OperationOutcome outcome = new FhirContext().newXmlParser().parseResource(OperationOutcome.class, new StringReader(responseContent));
		assertEquals("FOOBAR", outcome.getIssueFirstRep().getDetails().getValue());

	}

	@Test
	public void testDateRangeParam() throws Exception {

		// HttpPost httpPost = new HttpPost("http://localhost:" + ourPort +
		// "/Patient/1");
		// httpPost.setEntity(new StringEntity("test",
		// ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?dateRange=%3E%3D2011-01-01&dateRange=%3C%3D2021-01-01");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());

		Patient patient = (Patient) ourCtx.newXmlParser().parseBundle(responseContent).getEntries().get(0).getResource();
		assertEquals(">=2011-01-01", patient.getName().get(0).getSuffix().get(0).getValue());
		assertEquals("<=2021-01-01", patient.getName().get(0).getSuffix().get(1).getValue());

	}

	@Test
	public void testDelete() throws Exception {

		// HttpPost httpPost = new HttpPost("http://localhost:" + ourPort +
		// "/Patient/1");
		// httpPost.setEntity(new StringEntity("test",
		// ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpDelete httpGet = new HttpDelete("http://localhost:" + ourPort + "/Patient/1234");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());

		OperationOutcome patient = ourCtx.newXmlParser().parseResource(OperationOutcome.class, responseContent);
		assertEquals("1234", patient.getIssueFirstRep().getDetails().getValue());

	}

	@Test
	public void testDeleteNoResponse() throws Exception {

		// HttpPost httpPost = new HttpPost("http://localhost:" + ourPort +
		// "/Patient/1");
		// httpPost.setEntity(new StringEntity("test",
		// ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpDelete httpGet = new HttpDelete("http://localhost:" + ourPort + "/DiagnosticReport/1234");
		HttpResponse status = ourClient.execute(httpGet);

		assertEquals(204, status.getStatusLine().getStatusCode());

	}

	@Test
	public void testEntryLinkSelf() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?withIncludes=include1&_include=include2&_include=include3");
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);
		BundleEntry entry0 = bundle.getEntries().get(0);
		assertEquals("http://localhost:" + ourPort + "/Patient/1", entry0.getLinkSelf().getValue());
		assertEquals("1", entry0.getId().getValue());

		httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?withIncludes=include1&_include=include2&_include=include3&_format=json");
		status = ourClient.execute(httpGet);
		responseContent = IOUtils.toString(status.getEntity().getContent());
		assertEquals(200, status.getStatusLine().getStatusCode());
		bundle = ourCtx.newJsonParser().parseBundle(responseContent);
		entry0 = bundle.getEntries().get(0);
		assertEquals("http://localhost:" + ourPort + "/Patient/1?_format=json", entry0.getLinkSelf().getValue());

	}

	@Test
	public void testFormatParamJson() throws Exception {

		// HttpPost httpPost = new HttpPost("http://localhost:" + ourPort +
		// "/Patient/1");
		// httpPost.setEntity(new StringEntity("test",
		// ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/1?_format=json");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());

		Patient patient = (Patient) ourCtx.newJsonParser().parseResource(responseContent);
		// assertEquals("PatientOne",
		// patient.getName().get(0).getGiven().get(0).getValue());

		ourLog.info(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(patient));

	}

	@Test
	public void testFormatParamXml() throws Exception {

		// HttpPost httpPost = new HttpPost("http://localhost:" + ourPort +
		// "/Patient/1");
		// httpPost.setEntity(new StringEntity("test",
		// ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/1?_format=xml");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Patient patient = (Patient) ourCtx.newXmlParser().parseResource(responseContent);
		assertEquals("PatientOne", patient.getName().get(0).getGiven().get(0).getValue());

	}

	@Test
	public void testGetById() throws Exception {

		// HttpPost httpPost = new HttpPost("http://localhost:" + ourPort +
		// "/Patient/1");
		// httpPost.setEntity(new StringEntity("test",
		// ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/1");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Patient patient = (Patient) ourCtx.newXmlParser().parseResource(responseContent);
		assertEquals("PatientOne", patient.getName().get(0).getGiven().get(0).getValue());

		/*
		 * Different ID
		 */

		httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/2");
		status = ourClient.execute(httpGet);

		responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.debug("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		patient = (Patient) ourCtx.newXmlParser().parseResource(responseContent);
		assertEquals("PatientTwo", patient.getName().get(0).getGiven().get(0).getValue());

		/*
		 * Bad ID
		 */

		httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/9999999");
		status = ourClient.execute(httpGet);

		responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.debug("Response was:\n{}", responseContent);

		assertEquals(404, status.getStatusLine().getStatusCode());

	}

	@Test
	public void testGetByVersionId() throws Exception {

		// HttpPost httpPost = new HttpPost("http://localhost:" + ourPort +
		// "/Patient/1");
		// httpPost.setEntity(new StringEntity("test",
		// ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/1/_history/999");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Patient patient = (Patient) ourCtx.newXmlParser().parseResource(responseContent);
		assertEquals("PatientOne", patient.getName().get(0).getGiven().get(0).getValue());
		assertEquals("999", patient.getName().get(0).getText().getValue());

	}

	@Test
	public void testGetMetadata() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/metadata");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		// ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		IParser parser = ourCtx.newXmlParser().setPrettyPrint(true);
		Conformance bundle = parser.parseResource(Conformance.class, responseContent);

		{
			IParser p = ourCtx.newXmlParser().setPrettyPrint(true);
			String enc = p.encodeResourceToString(bundle);
			ourLog.info("Response:\n{}", enc);
			assertTrue(enc.contains(ExtensionConstants.CONF_ALSO_CHAIN));
		}
		// {
		// IParser p = ourCtx.newJsonParser().setPrettyPrint(true);
		//
		// p.encodeResourceToWriter(bundle, new OutputStreamWriter(System.out));
		//
		// String enc = p.encodeResourceToString(bundle);
		// ourLog.info("Response:\n{}", enc);
		// assertTrue(enc.contains(ExtensionConstants.CONF_ALSO_CHAIN));
		//
		// }
	}

	// @Test
	// public void testSearchByComplex() throws Exception {
	//
	// HttpGet httpGet = new HttpGet("http://localhost:" + ourPort +
	// "/Patient?Patient.identifier=urn:oid:2.16.840.1.113883.3.239.18.148%7C7000135&name=urn:oid:1.3.6.1.4.1.12201.102.5%7C522&date=");
	// HttpResponse status = ourClient.execute(httpGet);
	//
	// String responseContent =
	// IOUtils.toString(status.getEntity().getContent());
	// ourLog.info("Response was:\n{}", responseContent);
	//
	// assertEquals(200, status.getStatusLine().getStatusCode());
	// }

	@Test
	public void testHistoryResourceInstance() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/222/_history");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(2, bundle.getEntries().size());

		// Older resource
		{
			BundleEntry olderEntry = bundle.getEntries().get(0);
			assertEquals("222", olderEntry.getId().getValue());
			assertThat(olderEntry.getLinkSelf().getValue(), StringEndsWith.endsWith("/Patient/222/_history/1"));
			InstantDt pubExpected = new InstantDt(new Date(10000L));
			InstantDt pubActualRes = (InstantDt) olderEntry.getResource().getResourceMetadata().get(ResourceMetadataKeyEnum.PUBLISHED);
			InstantDt pubActualBundle = olderEntry.getPublished();
			assertEquals(pubExpected.getValueAsString(), pubActualRes.getValueAsString());
			assertEquals(pubExpected.getValueAsString(), pubActualBundle.getValueAsString());
			InstantDt updExpected = new InstantDt(new Date(20000L));
			InstantDt updActualRes = (InstantDt) olderEntry.getResource().getResourceMetadata().get(ResourceMetadataKeyEnum.UPDATED);
			InstantDt updActualBundle = olderEntry.getUpdated();
			assertEquals(updExpected.getValueAsString(), updActualRes.getValueAsString());
			assertEquals(updExpected.getValueAsString(), updActualBundle.getValueAsString());
		}
		// Newer resource
		{
			BundleEntry newerEntry = bundle.getEntries().get(1);
			assertEquals("222", newerEntry.getId().getValue());
			assertThat(newerEntry.getLinkSelf().getValue(), StringEndsWith.endsWith("/Patient/222/_history/2"));
			InstantDt pubExpected = new InstantDt(new Date(10000L));
			InstantDt pubActualRes = (InstantDt) newerEntry.getResource().getResourceMetadata().get(ResourceMetadataKeyEnum.PUBLISHED);
			InstantDt pubActualBundle = newerEntry.getPublished();
			assertEquals(pubExpected.getValueAsString(), pubActualRes.getValueAsString());
			assertEquals(pubExpected.getValueAsString(), pubActualBundle.getValueAsString());
			InstantDt updExpected = new InstantDt(new Date(30000L));
			InstantDt updActualRes = (InstantDt) newerEntry.getResource().getResourceMetadata().get(ResourceMetadataKeyEnum.UPDATED);
			InstantDt updActualBundle = newerEntry.getUpdated();
			assertEquals(updExpected.getValueAsString(), updActualRes.getValueAsString());
			assertEquals(updExpected.getValueAsString(), updActualBundle.getValueAsString());
		}

	}

	@Test
	public void testHistoryResourceType() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/_history");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(2, bundle.getEntries().size());

		// Older resource
		{
			BundleEntry olderEntry = bundle.getEntries().get(0);
			assertEquals("1", olderEntry.getId().getValue());
			assertThat(olderEntry.getLinkSelf().getValue(), StringEndsWith.endsWith("/Patient/1/_history/1"));
			InstantDt pubExpected = new InstantDt(new Date(10000L));
			InstantDt pubActualRes = (InstantDt) olderEntry.getResource().getResourceMetadata().get(ResourceMetadataKeyEnum.PUBLISHED);
			InstantDt pubActualBundle = olderEntry.getPublished();
			assertEquals(pubExpected.getValueAsString(), pubActualRes.getValueAsString());
			assertEquals(pubExpected.getValueAsString(), pubActualBundle.getValueAsString());
			InstantDt updExpected = new InstantDt(new Date(20000L));
			InstantDt updActualRes = (InstantDt) olderEntry.getResource().getResourceMetadata().get(ResourceMetadataKeyEnum.UPDATED);
			InstantDt updActualBundle = olderEntry.getUpdated();
			assertEquals(updExpected.getValueAsString(), updActualRes.getValueAsString());
			assertEquals(updExpected.getValueAsString(), updActualBundle.getValueAsString());
		}
		// Newer resource
		{
			BundleEntry newerEntry = bundle.getEntries().get(1);
			assertEquals("1", newerEntry.getId().getValue());
			assertThat(newerEntry.getLinkSelf().getValue(), StringEndsWith.endsWith("/Patient/1/_history/2"));
			InstantDt pubExpected = new InstantDt(new Date(10000L));
			InstantDt pubActualRes = (InstantDt) newerEntry.getResource().getResourceMetadata().get(ResourceMetadataKeyEnum.PUBLISHED);
			InstantDt pubActualBundle = newerEntry.getPublished();
			assertEquals(pubExpected.getValueAsString(), pubActualRes.getValueAsString());
			assertEquals(pubExpected.getValueAsString(), pubActualBundle.getValueAsString());
			InstantDt updExpected = new InstantDt(new Date(30000L));
			InstantDt updActualRes = (InstantDt) newerEntry.getResource().getResourceMetadata().get(ResourceMetadataKeyEnum.UPDATED);
			InstantDt updActualBundle = newerEntry.getUpdated();
			assertEquals(updExpected.getValueAsString(), updActualRes.getValueAsString());
			assertEquals(updExpected.getValueAsString(), updActualBundle.getValueAsString());
		}

	}

	@Test
	public void testPrettyPrint() throws Exception {

		// HttpPost httpPost = new HttpPost("http://localhost:" + ourPort +
		// "/Patient/1");
		// httpPost.setEntity(new StringEntity("test",
		// ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/1");
		HttpResponse status = ourClient.execute(httpGet);
		String responseContent = IOUtils.toString(status.getEntity().getContent());
		assertThat(responseContent, StringContains.containsString("<identifier><use"));

		httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/1?_pretty=false");
		status = ourClient.execute(httpGet);
		responseContent = IOUtils.toString(status.getEntity().getContent());
		assertThat(responseContent, StringContains.containsString("<identifier><use"));

		httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/1?_pretty=true");
		status = ourClient.execute(httpGet);
		responseContent = IOUtils.toString(status.getEntity().getContent());
		assertThat(responseContent, IsNot.not(StringContains.containsString("<identifier><use")));

	}

	@Test
	public void testSearchAll() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(2, bundle.getEntries().size());

		HttpPost httpPost = new HttpPost("http://localhost:" + ourPort + "/Patient/_search");
		status = ourClient.execute(httpPost);

		responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(2, bundle.getEntries().size());

	}
	
	
	@Test
	public void testReadOnTypeThatDoesntSupportRead() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/AdverseReaction/223");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(Constants.STATUS_HTTP_404_NOT_FOUND, status.getStatusLine().getStatusCode());

	}

	@Test
	public void testSearchAllProfiles() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Profile?");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		// ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		IParser parser = ourCtx.newXmlParser().setPrettyPrint(true);
		Bundle bundle = parser.parseBundle(responseContent);

		ourLog.info("Response:\n{}", parser.encodeBundleToString(bundle));

	}

	@Test
	public void testSearchByDob() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?dob=2011-01-02");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(1, bundle.getEntries().size());

		Patient patient = (Patient) bundle.getEntries().get(0).getResource();
		assertEquals("NONE", patient.getIdentifier().get(1).getValue().getValue());
		assertEquals("2011-01-02", patient.getIdentifier().get(2).getValue().getValue());

		/*
		 * With comparator
		 */

		httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?dob=%3E%3D2011-01-02");
		status = ourClient.execute(httpGet);

		responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(1, bundle.getEntries().size());

		patient = (Patient) bundle.getEntries().get(0).getResource();
		assertEquals(">=", patient.getIdentifier().get(1).getValue().getValue());
		assertEquals("2011-01-02", patient.getIdentifier().get(2).getValue().getValue());

	}

	@Test
	public void testSearchByDobWithSearchActionAndPost() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/_search?dob=2011-01-02");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(1, bundle.getEntries().size());

		Patient patient = (Patient) bundle.getEntries().get(0).getResource();
		assertEquals("NONE", patient.getIdentifier().get(1).getValue().getValue());
		assertEquals("2011-01-02", patient.getIdentifier().get(2).getValue().getValue());

		// POST

		HttpPost httpPost = new HttpPost("http://localhost:" + ourPort + "/Patient/_search?dob=2011-01-02");
		status = ourClient.execute(httpPost);

		responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(1, bundle.getEntries().size());

		patient = (Patient) bundle.getEntries().get(0).getResource();
		assertEquals("NONE", patient.getIdentifier().get(1).getValue().getValue());
		assertEquals("2011-01-02", patient.getIdentifier().get(2).getValue().getValue());

		// POST with form encoded

		httpPost = new HttpPost("http://localhost:" + ourPort + "/Patient/_search");
		List<BasicNameValuePair> urlParameters = new ArrayList<BasicNameValuePair>();
		urlParameters.add(new BasicNameValuePair("dob", "2011-01-02"));
		httpPost.setEntity(new UrlEncodedFormEntity(urlParameters));
		status = ourClient.execute(httpPost);

		responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(1, bundle.getEntries().size());

		patient = (Patient) bundle.getEntries().get(0).getResource();
		assertEquals("NONE", patient.getIdentifier().get(1).getValue().getValue());
		assertEquals("2011-01-02", patient.getIdentifier().get(2).getValue().getValue());

	}

	@Test
	public void testSearchByMultipleIdentifiers() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?ids=urn:aaa%7Caaa,urn:bbb%7Cbbb");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(1, bundle.getEntries().size());

		Patient patient = (Patient) bundle.getEntries().get(0).getResource();
		assertEquals("urn:aaa|aaa", patient.getIdentifier().get(1).getValueAsQueryToken());
		assertEquals("urn:bbb|bbb", patient.getIdentifier().get(2).getValueAsQueryToken());
	}

	@Test
	public void testSearchByParamIdentifier() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?identifier=urn:hapitest:mrns%7C00001");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(1, bundle.getEntries().size());

		Patient patient = (Patient) bundle.getEntries().get(0).getResource();
		assertEquals("PatientOne", patient.getName().get(0).getGiven().get(0).getValue());

		/**
		 * Alternate form
		 */
		HttpPost httpPost = new HttpPost("http://localhost:" + ourPort + "/Patient/_search?identifier=urn:hapitest:mrns%7C00001");
		status = ourClient.execute(httpPost);

		responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(1, bundle.getEntries().size());

		patient = (Patient) bundle.getEntries().get(0).getResource();
		assertEquals("PatientOne", patient.getName().get(0).getGiven().get(0).getValue());

		/**
		 * failing form
		 */
		httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/_search?identifier=urn:hapitest:mrns%7C00001");
		status = ourClient.execute(httpGet);

		responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());

	}

	@Test
	public void testSearchNamedNoParams() throws Exception {

		// HttpPost httpPost = new HttpPost("http://localhost:" + ourPort +
		// "/Patient/1");
		// httpPost.setEntity(new StringEntity("test",
		// ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/?_query=someQueryNoParams");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Patient patient = (Patient) ourCtx.newXmlParser().parseBundle(responseContent).getEntries().get(0).getResource();
		assertEquals("someQueryNoParams", patient.getName().get(1).getFamilyAsSingleString());

		InstantDt lm = (InstantDt) patient.getResourceMetadata().get(ResourceMetadataKeyEnum.UPDATED);
		assertEquals("2011-01-02T22:01:02", lm.getValueAsString());

	}

	@Test
	public void testSearchNamedOneParam() throws Exception {

		// HttpPost httpPost = new HttpPost("http://localhost:" + ourPort +
		// "/Patient/1");
		// httpPost.setEntity(new StringEntity("test",
		// ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient/?_query=someQueryOneParam&param1=AAAA");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Patient patient = (Patient) ourCtx.newXmlParser().parseBundle(responseContent).getEntries().get(0).getResource();
		assertEquals("AAAA", patient.getName().get(1).getFamilyAsSingleString());

	}

	@Test
	public void testSearchWithIncludes() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?withIncludes=include1&_include=include2&_include=include3");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		BundleEntry entry0 = bundle.getEntries().get(0);
		Patient patient = (Patient) entry0.getResource();
		assertEquals("include1", patient.getCommunication().get(0).getText().getValue());
		assertEquals("include2", patient.getAddress().get(0).getLine().get(0).getValue());
		assertEquals("include3", patient.getAddress().get(1).getLine().get(0).getValue());
	}

	@Test
	public void testSearchWithIncludesBad() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?withIncludes=include1&_include=include2&_include=include4");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(400, status.getStatusLine().getStatusCode());
	}

	@Test
	public void testSearchWithIncludesNone() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?withIncludes=include1");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		// Make sure there is no crash
		assertEquals(200, status.getStatusLine().getStatusCode());
	}

	@Test
	public void testSearchWithOptionalParam() throws Exception {

		HttpGet httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?name1=AAA");
		HttpResponse status = ourClient.execute(httpGet);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		Bundle bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(1, bundle.getEntries().size());

		Patient patient = (Patient) bundle.getEntries().get(0).getResource();
		assertEquals("AAA", patient.getName().get(0).getFamily().get(0).getValue());
		assertEquals("PatientOne", patient.getName().get(0).getGiven().get(0).getValue());

		/*
		 * Now with optional value populated
		 */

		httpGet = new HttpGet("http://localhost:" + ourPort + "/Patient?name1=AAA&name2=BBB");
		status = ourClient.execute(httpGet);

		responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		bundle = ourCtx.newXmlParser().parseBundle(responseContent);

		assertEquals(1, bundle.getEntries().size());

		patient = (Patient) bundle.getEntries().get(0).getResource();
		assertEquals("AAA", patient.getName().get(0).getFamily().get(0).getValue());
		assertEquals("BBB", patient.getName().get(0).getGiven().get(0).getValue());

	}

	@Test
	public void testUpdate() throws Exception {

		Patient patient = new Patient();
		patient.addIdentifier().setValue("002");

		HttpPut httpPost = new HttpPut("http://localhost:" + ourPort + "/Patient/001");
		httpPost.setEntity(new StringEntity(new FhirContext().newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPost);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		OperationOutcome oo =new FhirContext().newXmlParser().parseResource(OperationOutcome.class, responseContent);
		assertEquals("OODETAILS", oo.getIssueFirstRep().getDetails().getValue());
		
		assertEquals(200, status.getStatusLine().getStatusCode());
		assertEquals("http://localhost:" + ourPort + "/Patient/001/_history/002", status.getFirstHeader("Location").getValue());

	}

	
	public void testUpdateWrongResourceType() throws Exception {

		// TODO: this method sends in the wrong resource type vs. the URL so it should
		// give a useful error message (and then make this unit test actually run)
		Patient patient = new Patient();
		patient.addIdentifier().setValue("002");

		HttpPut httpPost = new HttpPut("http://localhost:" + ourPort + "/DiagnosticReport/001");
		httpPost.setEntity(new StringEntity(new FhirContext().newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPost);

		assertEquals(201, status.getStatusLine().getStatusCode());
		assertEquals("http://localhost:" + ourPort + "/DiagnosticReport/001/_history/002", status.getFirstHeader("Location").getValue());

	}
	
	@Test
	public void testUpdateNoResponse() throws Exception {

		DiagnosticReport dr = new DiagnosticReport();
		dr.addCodedDiagnosis().addCoding().setCode("AAA");

		HttpPut httpPost = new HttpPut("http://localhost:" + ourPort + "/DiagnosticReport/001");
		httpPost.setEntity(new StringEntity(new FhirContext().newXmlParser().encodeResourceToString(dr), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPost);

		assertEquals(204, status.getStatusLine().getStatusCode());
		assertEquals("http://localhost:" + ourPort + "/DiagnosticReport/001/_history/002", status.getFirstHeader("Location").getValue());

	}
	
	
	@Test
	public void testUpdateWithVersion() throws Exception {

		DiagnosticReport patient = new DiagnosticReport();
		patient.getIdentifier().setValue("001");

		HttpPut httpPut = new HttpPut("http://localhost:" + ourPort + "/DiagnosticReport/001");
		httpPut.addHeader("Content-Location", "/DiagnosticReport/001/_history/004");
		httpPut.setEntity(new StringEntity(new FhirContext().newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPut);

//		String responseContent = IOUtils.toString(status.getEntity().getContent());
//		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(204, status.getStatusLine().getStatusCode());
		assertNull(status.getEntity());
		assertEquals("http://localhost:" + ourPort + "/DiagnosticReport/001/_history/004", status.getFirstHeader("Location").getValue());

	}

	@Test()
	public void testUpdateWithVersionBadContentLocationHeader() throws Exception {

		DiagnosticReport patient = new DiagnosticReport();
		patient.getIdentifier().setValue("001");

		HttpPut httpPut = new HttpPut("http://localhost:" + ourPort + "/DiagnosticReport/001");
		httpPut.addHeader("Content-Location", "/Patient/001/_history/002");
		httpPut.setEntity(new StringEntity(new FhirContext().newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		CloseableHttpResponse results = ourClient.execute(httpPut);
		String responseContent = IOUtils.toString(results.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(400, results.getStatusLine().getStatusCode());
	}

	@Test
	public void testValidate() throws Exception {

		Patient patient = new Patient();
		patient.addName().addFamily("FOO");

		HttpPost httpPost = new HttpPost("http://localhost:" + ourPort + "/Patient/_validate");
		httpPost.setEntity(new StringEntity(new FhirContext().newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		HttpResponse status = ourClient.execute(httpPost);

		String responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(200, status.getStatusLine().getStatusCode());
		OperationOutcome oo = new FhirContext().newXmlParser().parseResource(OperationOutcome.class, responseContent);
		assertEquals("it passed", oo.getIssueFirstRep().getDetails().getValue());

		// Now should fail

		patient = new Patient();
		patient.addName().addFamily("BAR");

		httpPost = new HttpPost("http://localhost:" + ourPort + "/Patient/_validate");
		httpPost.setEntity(new StringEntity(new FhirContext().newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		status = ourClient.execute(httpPost);

		responseContent = IOUtils.toString(status.getEntity().getContent());
		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(422, status.getStatusLine().getStatusCode());
		oo = new FhirContext().newXmlParser().parseResource(OperationOutcome.class, responseContent);
		assertEquals("it failed", oo.getIssueFirstRep().getDetails().getValue());

		// Should fail with outcome

		patient = new Patient();
		patient.addName().addFamily("BAZ");

		httpPost = new HttpPost("http://localhost:" + ourPort + "/Patient/_validate");
		httpPost.setEntity(new StringEntity(new FhirContext().newXmlParser().encodeResourceToString(patient), ContentType.create(Constants.CT_FHIR_XML, "UTF-8")));

		status = ourClient.execute(httpPost);

//		responseContent = IOUtils.toString(status.getEntity().getContent());
//		ourLog.info("Response was:\n{}", responseContent);

		assertEquals(204, status.getStatusLine().getStatusCode());
		assertNull(status.getEntity());
//		assertEquals("", responseContent);

	}

	@AfterClass
	public static void afterClass() throws Exception {
		ourServer.stop();
	}

	@BeforeClass
	public static void beforeClass() throws Exception {
		ourPort = RandomServerPortProvider.findFreePort();
		ourServer = new Server(ourPort);
		ourCtx = new FhirContext(Patient.class);

		DummyPatientResourceProvider patientProvider = new DummyPatientResourceProvider();
		ServerProfileProvider profProvider = new ServerProfileProvider(ourCtx);
		DummyDiagnosticReportResourceProvider reportProvider = new DummyDiagnosticReportResourceProvider();
		DummyAdverseReactionResourceProvider adv = new DummyAdverseReactionResourceProvider();

		ServletHandler proxyHandler = new ServletHandler();
		DummyRestfulServer servlet = new DummyRestfulServer(patientProvider, profProvider, reportProvider, adv);
		ServletHolder servletHolder = new ServletHolder(servlet);
		proxyHandler.addServletWithMapping(servletHolder, "/*");
		ourServer.setHandler(proxyHandler);
		ourServer.start();

		PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager(5000, TimeUnit.MILLISECONDS);
		HttpClientBuilder builder = HttpClientBuilder.create();
		builder.setConnectionManager(connectionManager);
		ourClient = builder.build();

	}

	public static class DummyDiagnosticReportResourceProvider implements IResourceProvider {

		/**
		 * @param theValue
		 */
		@Search
		public DiagnosticReport alwaysThrow404(@RequiredParam(name = "throw404") StringDt theValue) {
			throw new ResourceNotFoundException("AAAABBBB");
		}

		@SuppressWarnings("unused")
		@Create()
		public MethodOutcome createDiagnosticReport(@ResourceParam DiagnosticReport thePatient) {
			OperationOutcome outcome = new OperationOutcome();
			outcome.addIssue().setDetails("FOOBAR");
			throw new UnprocessableEntityException(outcome);
		}

		@SuppressWarnings("unused")
		@Delete()
		public void deleteDiagnosticReport(@IdParam IdDt theId) {
			// do nothing
		}

		@Override
		public Class<? extends IResource> getResourceType() {
			return DiagnosticReport.class;
		}

		@SuppressWarnings("unused")
		@Update()
		public MethodOutcome updateDiagnosticReportWithNoResponse(@IdParam IdDt theId, @VersionIdParam IdDt theVersionId, @ResourceParam DiagnosticReport thePatient) {
			IdDt id = theId;
			IdDt version = theVersionId;
			return new MethodOutcome(id, version);
		}

		@SuppressWarnings("unused")
		@Update()
		public MethodOutcome updateDiagnosticReportWithVersionAndNoResponse(@IdParam IdDt theId, @ResourceParam DiagnosticReport thePatient) {
			IdDt id = theId;
			IdDt version = new IdDt("002");
			return new MethodOutcome(id, version);
		}

	}

	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class DummyAdverseReactionResourceProvider implements IResourceProvider {

		/*
		 * *********************
		 * NO NEW METHODS
		 * *********************
		 */
		
		@Override
		public Class<? extends IResource> getResourceType() {
			return AdverseReaction.class;
		}
		
		@Create()
		public MethodOutcome create(@ResourceParam AdverseReaction thePatient) {
			IdDt id = new IdDt(thePatient.getIdentifier().get(0).getValue().getValue());
			IdDt version = new IdDt(thePatient.getIdentifier().get(1).getValue().getValue());
			return new MethodOutcome(id, version);
		}
		
	}
	
	/**
	 * Created by dsotnikov on 2/25/2014.
	 */
	public static class DummyPatientResourceProvider implements IResourceProvider {

		@Create()
		public MethodOutcome createPatient(@ResourceParam Patient thePatient) {
			IdDt id = new IdDt(thePatient.getIdentifier().get(0).getValue().getValue());
			IdDt version = new IdDt(thePatient.getIdentifier().get(1).getValue().getValue());
			return new MethodOutcome(id, version);
		}

		@Delete()
		public MethodOutcome deletePatient(@IdParam IdDt theId) {
			MethodOutcome retVal = new MethodOutcome();
			retVal.setOperationOutcome(new OperationOutcome());
			retVal.getOperationOutcome().addIssue().setDetails(theId.getValue());
			return retVal;
		}

		@SuppressWarnings("unused")
		public List<Patient> findDiagnosticReportsByPatient(@RequiredParam(name = "Patient.identifier") IdentifierDt thePatientId, @RequiredParam(name = DiagnosticReport.SP_NAME) CodingListParam theNames, @OptionalParam(name = DiagnosticReport.SP_DATE) DateRangeParam theDateRange)
				throws Exception {
			return Collections.emptyList();
		}

		@History
		public List<Patient> getHistoryResourceInstance(@IdParam IdDt theId) {
			ArrayList<Patient> retVal = new ArrayList<Patient>();

			Patient older = createPatient1();
			older.setId(theId);
			older.getNameFirstRep().getFamilyFirstRep().setValue("OlderFamily");
			older.getResourceMetadata().put(ResourceMetadataKeyEnum.VERSION_ID, "1");
			older.getResourceMetadata().put(ResourceMetadataKeyEnum.PUBLISHED, new Date(10000L));
			older.getResourceMetadata().put(ResourceMetadataKeyEnum.UPDATED, new InstantDt(new Date(20000L)));
			older.getResourceMetadata().put(ResourceMetadataKeyEnum.VERSION_ID, "1");
			retVal.add(older);

			Patient newer = createPatient1();
			newer.setId(theId);
			newer.getNameFirstRep().getFamilyFirstRep().setValue("NewerFamily");
			newer.getResourceMetadata().put(ResourceMetadataKeyEnum.VERSION_ID, "2");
			newer.getResourceMetadata().put(ResourceMetadataKeyEnum.PUBLISHED, new Date(10000L));
			newer.getResourceMetadata().put(ResourceMetadataKeyEnum.UPDATED, new InstantDt(new Date(30000L)));
			retVal.add(newer);

			return retVal;
		}

		@History
		public List<Patient> getHistoryResourceType() {
			ArrayList<Patient> retVal = new ArrayList<Patient>();

			Patient older = createPatient1();
			older.getNameFirstRep().getFamilyFirstRep().setValue("OlderFamily");
			older.getResourceMetadata().put(ResourceMetadataKeyEnum.VERSION_ID, "1");
			older.getResourceMetadata().put(ResourceMetadataKeyEnum.PUBLISHED, new Date(10000L));
			older.getResourceMetadata().put(ResourceMetadataKeyEnum.UPDATED, new InstantDt(new Date(20000L)));
			older.getResourceMetadata().put(ResourceMetadataKeyEnum.VERSION_ID, "1");
			retVal.add(older);

			Patient newer = createPatient1();
			newer.getNameFirstRep().getFamilyFirstRep().setValue("NewerFamily");
			newer.getResourceMetadata().put(ResourceMetadataKeyEnum.VERSION_ID, "2");
			newer.getResourceMetadata().put(ResourceMetadataKeyEnum.PUBLISHED, new Date(10000L));
			newer.getResourceMetadata().put(ResourceMetadataKeyEnum.UPDATED, new InstantDt(new Date(30000L)));
			retVal.add(newer);

			return retVal;
		}

		public Map<String, Patient> getIdToPatient() {
			Map<String, Patient> idToPatient = new HashMap<String, Patient>();
			{
				Patient patient = createPatient1();
				idToPatient.put("1", patient);
			}
			{
				Patient patient = new Patient();
				patient.getIdentifier().add(new IdentifierDt());
				patient.getIdentifier().get(0).setUse(IdentifierUseEnum.OFFICIAL);
				patient.getIdentifier().get(0).setSystem(new UriDt("urn:hapitest:mrns"));
				patient.getIdentifier().get(0).setValue("00002");
				patient.getName().add(new HumanNameDt());
				patient.getName().get(0).addFamily("Test");
				patient.getName().get(0).addGiven("PatientTwo");
				patient.getGender().setText("F");
				patient.getId().setValue("2");
				idToPatient.put("2", patient);
			}
			return idToPatient;
		}

		@Search()
		public Patient getPatient(@RequiredParam(name = Patient.SP_IDENTIFIER) IdentifierDt theIdentifier) {
			for (Patient next : getIdToPatient().values()) {
				for (IdentifierDt nextId : next.getIdentifier()) {
					if (nextId.matchesSystemAndValue(theIdentifier)) {
						return next;
					}
				}
			}
			return null;
		}

		@Search()
		public Patient getPatientByDateRange(@RequiredParam(name = "dateRange") DateRangeParam theIdentifiers) {
			Patient retVal = getIdToPatient().get("1");
			retVal.getName().get(0).addSuffix().setValue(theIdentifiers.getLowerBound().getValueAsQueryToken());
			retVal.getName().get(0).addSuffix().setValue(theIdentifiers.getUpperBound().getValueAsQueryToken());
			return retVal;
		}

		@Search()
		public List<Patient> getPatientMultipleIdentifiers(@RequiredParam(name = "ids") CodingListParam theIdentifiers) {
			List<Patient> retVal = new ArrayList<Patient>();
			Patient next = getIdToPatient().get("1");

			for (CodingDt nextId : theIdentifiers.getCodings()) {
				next.getIdentifier().add(new IdentifierDt(nextId.getSystem().getValueAsString(), nextId.getCode().getValue()));
			}

			retVal.add(next);

			return retVal;
		}

		@Search(queryName = "someQueryNoParams")
		public Patient getPatientNoParams() {
			Patient next = getIdToPatient().get("1");
			next.addName().addFamily("someQueryNoParams");
			next.getResourceMetadata().put(ResourceMetadataKeyEnum.UPDATED, new InstantDt("2011-01-02T22:01:02"));
			return next;
		}

		@Search(queryName = "someQueryOneParam")
		public Patient getPatientOneParam(@RequiredParam(name = "param1") StringDt theParam) {
			Patient next = getIdToPatient().get("1");
			next.addName().addFamily(theParam.getValue());
			return next;
		}

		@Search()
		public Patient getPatientWithDOB(@RequiredParam(name = "dob") QualifiedDateParam theDob) {
			Patient next = getIdToPatient().get("1");
			if (theDob.getComparator() != null) {
				next.addIdentifier().setValue(theDob.getComparator().getCode());
			} else {
				next.addIdentifier().setValue("NONE");
			}
			next.addIdentifier().setValue(theDob.getValueAsString());
			return next;
		}

		@Search()
		public Patient getPatientWithIncludes(@RequiredParam(name = "withIncludes") StringDt theString, @IncludeParam(allow = { "include1", "include2", "include3" }) List<PathSpecification> theIncludes) {
			Patient next = getIdToPatient().get("1");

			next.addCommunication().setText(theString.getValue());

			for (PathSpecification line : theIncludes) {
				next.addAddress().addLine(line.getValue());
			}

			return next;
		}

		@Search()
		public List<Patient> getPatientWithOptionalName(@RequiredParam(name = "name1") StringDt theName1, @OptionalParam(name = "name2") StringDt theName2) {
			List<Patient> retVal = new ArrayList<Patient>();
			Patient next = getIdToPatient().get("1");
			next.getName().get(0).getFamily().set(0, theName1);
			if (theName2 != null) {
				next.getName().get(0).getGiven().set(0, theName2);
			}
			retVal.add(next);

			return retVal;
		}

		/**
		 * @param theName3
		 */
		@Search()
		public List<Patient> getPatientWithOptionalName(@RequiredParam(name = "aaa") StringDt theName1, @OptionalParam(name = "bbb") StringDt theName2, @OptionalParam(name = "ccc") StringDt theName3) {
			List<Patient> retVal = new ArrayList<Patient>();
			Patient next = getIdToPatient().get("1");
			next.getName().get(0).getFamily().set(0, theName1);
			if (theName2 != null) {
				next.getName().get(0).getGiven().set(0, theName2);
			}
			retVal.add(next);

			return retVal;
		}

		/**
		 * Retrieve the resource by its identifier
		 * 
		 * @param theId
		 *            The resource identity
		 * @return The resource
		 */
		@Read()
		public Patient getResourceById(@IdParam IdDt theId) {
			return getIdToPatient().get(theId.getValue());
		}

		@Read()
		public Patient getResourceById(@IdParam IdDt theId, @VersionIdParam IdDt theVersionId) {
			Patient retVal = getIdToPatient().get(theId.getValue());
			retVal.getName().get(0).setText(theVersionId.getValue());
			return retVal;
		}

		@Search()
		public Collection<Patient> getResources() {
			return getIdToPatient().values();
		}

		@Override
		public Class<Patient> getResourceType() {
			return Patient.class;
		}

		@SuppressWarnings("unused")
		@Update()
		public MethodOutcome updateDiagnosticReportWithVersion(@IdParam IdDt theId, @VersionIdParam IdDt theVersionId, @ResourceParam DiagnosticOrder thePatient) {
			/*
			 * TODO: THIS METHOD IS NOT USED. It's the wrong type
			 * (DiagnosticOrder), so it should cause an exception on startup.
			 * Also we should detect if there are multiple resource params on an
			 * update/create/etc method
			 */
			IdDt id = theId;
			IdDt version = theVersionId;
			return new MethodOutcome(id, version);
		}

		@Update()
		public MethodOutcome updatePatient(@IdParam IdDt theId, @ResourceParam Patient thePatient) {
			IdDt id = theId;
			IdDt version = new IdDt(thePatient.getIdentifierFirstRep().getValue().getValue());
			OperationOutcome oo = new OperationOutcome();
			oo.addIssue().setDetails("OODETAILS");
			return new MethodOutcome(id, version, oo);
		}

		@Validate()
		public MethodOutcome validatePatient(@ResourceParam Patient thePatient) {
			if (thePatient.getNameFirstRep().getFamilyFirstRep().getValueNotNull().equals("FOO")) {
				MethodOutcome methodOutcome = new MethodOutcome();
				OperationOutcome oo = new OperationOutcome();
				oo.addIssue().setDetails("it passed");
				methodOutcome.setOperationOutcome(oo);
				return methodOutcome;
			}
			if (thePatient.getNameFirstRep().getFamilyFirstRep().getValueNotNull().equals("BAR")) {
				throw new UnprocessableEntityException("it failed");
			}
			return new MethodOutcome();
		}

		private Patient createPatient1() {
			Patient patient = new Patient();
			patient.addIdentifier();
			patient.getIdentifier().get(0).setUse(IdentifierUseEnum.OFFICIAL);
			patient.getIdentifier().get(0).setSystem(new UriDt("urn:hapitest:mrns"));
			patient.getIdentifier().get(0).setValue("00001");
			patient.addName();
			patient.getName().get(0).addFamily("Test");
			patient.getName().get(0).addGiven("PatientOne");
			patient.getGender().setText("M");
			patient.getId().setValue("1");
			return patient;
		}

	}

	public static class DummyRestfulServer extends RestfulServer {

		private static final long serialVersionUID = 1L;

		private Collection<IResourceProvider> myResourceProviders;

		public DummyRestfulServer(IResourceProvider... theResourceProviders) {
			myResourceProviders = Arrays.asList(theResourceProviders);
		}

		@Override
		public Collection<IResourceProvider> getResourceProviders() {
			return myResourceProviders;
		}

		@Override
		public ISecurityManager getSecurityManager() {
			return null;
		}

	}

}