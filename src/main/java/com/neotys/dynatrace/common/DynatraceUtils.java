package com.neotys.dynatrace.common;

import com.google.common.base.Optional;
//import com.neotys.dynatrace.common.data.DynatraceService;
import com.neotys.dynatrace.common.tag.DynatraceTaggingUtils;
import com.neotys.dynatrace.monitoring.timeseries.DynatraceMetric;
import com.neotys.extensions.action.engine.Context;
import com.neotys.extensions.action.engine.Proxy;
import com.neotys.extensions.action.engine.ProxyType;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.json.JSONArray;
//import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import static com.neotys.dynatrace.common.HTTPGenerator.HTTP_GET_METHOD;
import static com.neotys.dynatrace.common.HTTPGenerator.HTTP_POST_METHOD;
import static com.neotys.dynatrace.common.HTTPGenerator.HTTP_DELETE_METHOD;
import static com.neotys.dynatrace.common.HTTPGenerator.HTTP_PUT_METHOD;
import static com.neotys.dynatrace.common.HttpResponseUtils.getJsonArrayResponse;
import static com.neotys.dynatrace.common.HttpResponseUtils.getJsonResponse;

/**
 * Created by anouvel on 20/12/2017.
 */

// TODO : remove dead code - some methods were moved to topology walker

public class DynatraceUtils {
	// TODO : move constant Strings to 'Constants' class
	
	private static final String PARAM_TAG="tag";
	private static final String PARAM_ENTITY="entity";
	
    private static final String DYNATRACE_ENV1URL = "/api/v1/";
    private static final String DYNATRACE_ENV2URL = "/api/v2/";
    private static final String DYNATRACE_CONFIGURL = "/api/config/v1/";
    public  static final String DYNATRACE_SERVICE = "entity/services";
//    private static final String DYNATRACE_APPLICATIONNAME="entity/applications";
    public  static final String DYNATRACE_PROCESS="entity/infrastructure/processes";
    public  static final String DYNATRACE_PROCESSGROUP="entity/infrastructure/process-groups";
    public  static final String DYNATRACE_HOST="entity/infrastructure/hosts";
    private static final String DYNATRACE_PROTOCOL = "https://";
    private static final String DYNATRACE_TIMESERIES = "timeseries";
    private static final ImageIcon DYNATRACE_ICON;
//    private static final String DYNATRACE_TAG_PARAMETER ="tag=";
//    private static final String DYNATRACE_TAG_NEXTPARAMETER="&tag=";
//    private static final String DYNATRACE_SERVICE_PREFIX="SERVICE";
    private static final long DYNATRACE_DEFAULT_DIFF=120000;
    static {
        final URL iconURL = DynatraceUtils.class.getResource("dynatrace.png");
        if (iconURL != null) {
            DYNATRACE_ICON = new ImageIcon(iconURL);
        } else {
            DYNATRACE_ICON = null;
        }
    }

    private DynatraceUtils() {
    }

    public static ImageIcon getDynatraceIcon() {
        return DYNATRACE_ICON;
    }
/*
	public static void generateHeaders(final Map<String, String> header) {
		header.put("Accept", "application/json; charset=utf-8");
		header.put("Content-Type", "application/json");
	}
*/
	public static String cleanUpTags(String tag) {
		if (tag != null) {
			return tag.replaceAll(":", "\\\\:");
		} else
			return null;
	}

    public static MultivaluedMap<String,String> generateGetTagsParameters(Optional<String> tags,boolean usetag) {
    	MultivaluedMap<String,String> parameters=new MultivaluedHashMap<>();
        if(usetag) {
            if (tags.isPresent()) {
            	String tagcsv=tags.get();
            	String[] tagvalues=tagcsv.split(",");
            	for (String tag:tagvalues)
            		addGetParameterTag(parameters,tag);
            }
        }
        return parameters;
    }

    public static void addGetParameterTag(MultivaluedMap<String,String> params,String tag) {
    	params.add(PARAM_TAG,cleanUpTags(tag));
    }
    
    public static void addGetParameterEntity(MultivaluedMap<String,String> params,String entity) {
    	params.add(PARAM_ENTITY,entity);
    }
/*    
    public static Set<String> getServiceEntityIds(final Context context, final DynatraceContext dynatraceContext, final Optional<String> proxyName, final boolean traceMode)
            throws Exception {
        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_SERVICE;
        final MultivaluedMap<String, String> parameters = generateGetTagsParameters(dynatraceContext.getTags(),true);
        parameters.add("Api-Token", dynatraceContext.getApiKey());

        final Optional<Proxy> proxy = getProxy(context, proxyName, dynatraceUrl);
        final HTTPGenerator http = new HTTPGenerator(HTTP_GET_METHOD, dynatraceUrl, dynatraceContext.getHeaders(), parameters, proxy);
        final Set<String> serviceEntityIds = new HashSet<String>();
        try {
            if(traceMode){
                context.getLogger().info("Dynatrace service, get service entities:\n" + http.getRequest());
            }
            final HttpResponse httpResponse = http.execute();

            if (HttpResponseUtils.isSuccessHttpCode(httpResponse.getStatusLine().getStatusCode())) {
                final JSONArray jsonArrayResponse = getJsonArrayResponse(httpResponse);
                if (jsonArrayResponse != null) {
                    extractServiceEntityIdsFromResponse(serviceEntityIds, jsonArrayResponse);
                }
            } else {
                final String stringResponse = HttpResponseUtils.getStringResponse(httpResponse);
                throw new DynatraceException(httpResponse.getStatusLine().getReasonPhrase() + " - "+ dynatraceUrl + " - " + stringResponse);
            }
        } finally {
            http.closeHttpClient();
        }
        {
            context.getLogger().info("Found services: " + serviceEntityIds);
        }

        return serviceEntityIds;
    }
*/
    private static long getUtcDate(Optional<Long> delta) {
        long diff;
        if(delta.isPresent())
            diff=delta.get();
        else
            diff=DYNATRACE_DEFAULT_DIFF;

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        long timeInMillisSinceEpoch123 = now.toInstant().toEpochMilli();
        timeInMillisSinceEpoch123 -= diff;
        return timeInMillisSinceEpoch123;
    }

    public static List<DynatraceMetric> getTimeSeriesMetricData(final String timeSeries,
                                                                final String aggregate, final Set<String> listEntityId, final long startTS, final Context context, final DynatraceContext dynatraceContext, final boolean traceMode, final Optional<Long> difftimeseries,final Optional<Boolean> noDataExchangeAPI)
            throws Exception {
    	
        final List<DynatraceMetric> metrics = new ArrayList<>();
    	if (listEntityId.isEmpty())
    		return metrics;
    	

//        final String url = DynatraceUtils.getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_TIMESERIES + "/" + timeSeries;
//        final MultivaluedMap<String, String> parameters = generateGetTagsParameters(dynatraceContext.getTags(),true);
        //parameters.add("Api-Token", dynatraceContext.getApiKey());

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        final StringBuilder jsonEntitiesBuilder = new StringBuilder().append("{");
        if(aggregate != null){
            jsonEntitiesBuilder.append("\"aggregationType\": \"").append(aggregate.toLowerCase()).append("\",");
        }
        jsonEntitiesBuilder
                .append("\"timeseriesId\" : \"").append(timeSeries).append("\",")
                .append("\"endTimestamp\":\"").append(String.valueOf(now.toInstant().toEpochMilli())).append("\",")
                .append("\"startTimestamp\":\"").append(String.valueOf(getUtcDate(difftimeseries))).append("\",")
                .append("\"entities\":[");

        for (String entit : listEntityId) {
            jsonEntitiesBuilder.append("\"").append(entit).append("\",");
        }

        final String bodyJson = jsonEntitiesBuilder.substring(0, jsonEntitiesBuilder.length() - 1) + "]}";

        
//        final Optional<Proxy> proxy = getProxy(context, dynatraceContext.getProxyname(), url);
//        final HTTPGenerator http = new HTTPGenerator(HTTP_POST_METHOD, url,  dynatraceContext.getHeaders(), parameters, proxy, bodyJson);

//        try {
            JSONObject jsonMetricResults=executeDynatraceAPIPostObjectRequest(context, dynatraceContext, Api.ENV1, DYNATRACE_TIMESERIES + "/" + timeSeries, bodyJson, traceMode);

            if(traceMode){
                context.getLogger().info("Dynatrace service, get timeseries:\n" + Api.ENV1+"/"+ DYNATRACE_TIMESERIES + "\n" + bodyJson);
            }
//            final HttpResponse httpResponse = http.execute();

//            final int statusCode = httpResponse.getStatusLine().getStatusCode();
//            if (HttpResponseUtils.isSuccessHttpCode(statusCode)) {
//                jsonMetricResults = HttpResponseUtils.getJsonResponse(httpResponse);
                if (jsonMetricResults == null || !jsonMetricResults.has("result")) {
                    context.getLogger().debug("No timeseries found.");
                    return Collections.emptyList();
                }
                jsonMetricResults = jsonMetricResults.getJSONObject("result");
                if (jsonMetricResults.has("dataPoints") && jsonMetricResults.has("entities")) {
                    final JSONObject jsonEntity = jsonMetricResults.getJSONObject("entities");
                    final Map<String, String> entities = getEntityDefinition(jsonEntity);

                    final JSONObject jsonDataPoint = jsonMetricResults.getJSONObject("dataPoints");
                    final Iterator<?> keysIterator = jsonDataPoint.keys();
                    while (keysIterator.hasNext()) {
                        final String entity = (String) keysIterator.next();
                        final String displayName = getEntityDisplayName(entities, entity);
                        final JSONArray arr = jsonDataPoint.getJSONArray(entity);
                        addDataMetrics(startTS,metrics, jsonMetricResults, entity, displayName, arr,noDataExchangeAPI);
                    }
                }
                if(metrics.isEmpty() && traceMode){
                    context.getLogger().info("No timeseries found.");
                }
 /*               
            }
            else if(statusCode != HttpStatus.SC_BAD_REQUEST && statusCode != HttpStatus.SC_NOT_FOUND){
                final String stringResponse = HttpResponseUtils.getStringResponse(httpResponse);
                throw new DynatraceException(httpResponse.getStatusLine().getReasonPhrase() + " - "+ url + " - "+ bodyJson + " - " + stringResponse);
            }
        } finally {
            http.closeHttpClient();
        }
*/
        return metrics;
    }

    private static HashMap<String, String> getEntityDefinition(final JSONObject entity) {
        final HashMap<String, String> result = new HashMap<>();
        final Iterator<?> keysIterator = entity.keys();
        while (keysIterator.hasNext()) {
            final Object key = keysIterator.next();
            result.put((String) key, (String) entity.get((String) key));
        }
        return result;
    }


    private static String getEntityDisplayName(final Map<String, String> map, final String entity) {
        final String[] entities = entity.split(",");
        for (Map.Entry<String, String> e : map.entrySet()) {
            for (String entityFromMap : entities) {
                if (entityFromMap.equalsIgnoreCase(e.getKey())) {
                    return e.getValue();
                }
            }
        }
        return null;
    }

    private static void addDataMetrics(final long startTS, final List<DynatraceMetric> metrics, final JSONObject jsonApplication,
                                final String entity, final String displayName, final JSONArray jsonArray,final Optional<Boolean> noDataExchangeApi) {
        for (int i = 0; i < jsonArray.length(); i++) {
            final JSONArray data = jsonArray.getJSONArray(i);
            if (data.get(1) instanceof Double) {
                final long time = data.getLong(0);
                DateTime utcTime = new DateTime(time, DateTimeZone.UTC);
                final DateTime localTime = utcTime.withZone(DateTimeZone.getDefault());

                if(noDataExchangeApi.isPresent())
                {
                    if(noDataExchangeApi.get())
                    {
                        final String unit = jsonApplication.getString("unit");
                        final double value = data.getDouble(1);
                        final String timeseriesId = jsonApplication.getString("timeseriesId");
                        final DynatraceMetric metric = new DynatraceMetric(unit, value, localTime.getMillis(), displayName, timeseriesId, entity);
                        metrics.add(metric);
                    }
                    else
                    {
                        if (time >= startTS) {
                            final String unit = jsonApplication.getString("unit");
                            final double value = data.getDouble(1);
                            final String timeseriesId = jsonApplication.getString("timeseriesId");
                            final DynatraceMetric metric = new DynatraceMetric(unit, value, localTime.getMillis(), displayName, timeseriesId, entity);
                            metrics.add(metric);
                        }
                    }
                }
                else {
                    if (time >= startTS) {
                        final String unit = jsonApplication.getString("unit");
                        final double value = data.getDouble(1);
                        final String timeseriesId = jsonApplication.getString("timeseriesId");
                        final DynatraceMetric metric = new DynatraceMetric(unit, value, localTime.getMillis(), displayName, timeseriesId, entity);
                        metrics.add(metric);
                    }
                }
            }

        }
    }
/*
    public static Set<String> getListProcessGroupIDfromServiceId(final Context context, final DynatraceContext dynatraceContext, String serviceID, final Optional<String> proxyName, final boolean traceMode,final boolean usetags)
            throws Exception {
        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_SERVICE+"/"+serviceID;
        final MultivaluedMap<String, String> parameters =generateGetTagsParameters(dynatraceContext.getTags(),usetags);


        parameters.add("Api-Token", dynatraceContext.getApiKey());

//        DynatraceService dynatraceService = null;
        final Optional<Proxy> proxy = getProxy(context, proxyName, dynatraceUrl);
        final HTTPGenerator http = new HTTPGenerator(HTTP_GET_METHOD, dynatraceUrl, dynatraceContext.getHeaders(), parameters, proxy);
        final Set<String> processGroupIds = new HashSet<String>();
        try {
            if(traceMode){
                context.getLogger().info("Dynatrace service, get service entity:\n" + http.getRequest());
            }
            final HttpResponse httpResponse = http.execute();

            if (HttpResponseUtils.isSuccessHttpCode(httpResponse.getStatusLine().getStatusCode())) {
                final JSONObject jsonObjectResponse = getJsonResponse(httpResponse);
                if (jsonObjectResponse != null) {
                    extractProcessGroupIdsFromResponse(processGroupIds, jsonObjectResponse);
                }
            } else {
                final String stringResponse = HttpResponseUtils.getStringResponse(httpResponse);
                throw new DynatraceException(httpResponse.getStatusLine().getReasonPhrase() + " - "+ dynatraceUrl + " - " + stringResponse);
            }
        } finally {
            http.closeHttpClient();
        }

        if (traceMode) {
            context.getLogger().info("Found service infrastructure: " + processGroupIds);
        }

        return processGroupIds;
    }
*/
/*
    public static DynatraceService getListProcessGroupInstanceFromServiceId(final Context context, final DynatraceContext dynatraceContext, String serviceID, final Optional<String> proxyName, final boolean traceMode)
            throws Exception {
        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_SERVICE+"/"+serviceID;
        final MultivaluedMap<String, String> parameters = generateGetTagsParameters(dynatraceContext.getTags(),true);
        String servicename;

        parameters.add("Api-Token", dynatraceContext.getApiKey());

        DynatraceService dynatraceService = null;
        final Optional<Proxy> proxy = getProxy(context, proxyName, dynatraceUrl);
        final HTTPGenerator http = new HTTPGenerator(HTTP_GET_METHOD, dynatraceUrl, dynatraceContext.getHeaders(), parameters, proxy);
        final Set<String> pgInstanceEntityIds = new HashSet<String>();
        try {
            if(traceMode){
                context.getLogger().info("Dynatrace service, get processGroupInstance entities:\n" + http.getRequest());
            }
            final HttpResponse httpResponse = http.execute();

            if (HttpResponseUtils.isSuccessHttpCode(httpResponse.getStatusLine().getStatusCode())) {
                final JSONObject jsonObjectResponse = getJsonResponse(httpResponse);
                if (jsonObjectResponse != null) {
                    servicename=extractProcessGroupInstanceIdsFromResponse(pgInstanceEntityIds, jsonObjectResponse);
                    dynatraceService=new DynatraceService(serviceID,servicename,pgInstanceEntityIds);        
                }
            } else {
                final String stringResponse = HttpResponseUtils.getStringResponse(httpResponse);
                throw new DynatraceException(httpResponse.getStatusLine().getReasonPhrase() + " - "+ dynatraceUrl + " - " + stringResponse);
            }
        } finally {
            http.closeHttpClient();
        }

        if (traceMode) {
            context.getLogger().info("Found applications: " + pgInstanceEntityIds);
        }

        return dynatraceService;
    }
*/
/*
    public static Set<String> getHostIdFromProcessID(final Context context, final DynatraceContext dynatraceContext, String processId, final Optional<String> proxyName, final boolean traceMode) throws Exception {
        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_PROCESS+"/"+processId;
        final MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
        Set<String> hostid=new HashSet<String>();
        parameters.add("Api-Token", dynatraceContext.getApiKey());

        final Optional<Proxy> proxy = getProxy(context, proxyName, dynatraceUrl);
        final HTTPGenerator http = new HTTPGenerator(HTTP_GET_METHOD, dynatraceUrl, dynatraceContext.getHeaders(), parameters, proxy);

        try {
            if(traceMode){
                context.getLogger().info("Dynatrace process, get process entity:\n" + http.getRequest());
            }
            final HttpResponse httpResponse = http.execute();

            if (HttpResponseUtils.isSuccessHttpCode(httpResponse.getStatusLine().getStatusCode())) {
                final JSONObject jsonObjectresponse = getJsonResponse(httpResponse);
                if (jsonObjectresponse != null) {
                    extractHostIdsFromResponse(hostid,jsonObjectresponse);
                }
            } else {
                final String stringResponse = HttpResponseUtils.getStringResponse(httpResponse);
                throw new DynatraceException(httpResponse.getStatusLine().getReasonPhrase() + " - "+ dynatraceUrl + " - " + stringResponse);
            }
        } finally {
            http.closeHttpClient();
        }

        if (traceMode) {
            context.getLogger().info("Found Hosts: " + hostid);
        }

        return hostid;
    }
*/
/*    
    public static Set<String> getHostIdFromProcessGroupID(final Context context, final DynatraceContext dynatraceContext, String processId, final Optional<String> proxyName, final boolean traceMode) throws Exception {
        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_PROCESSGROUP+"/"+processId;
        final MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
        Set<String> hostid=new HashSet<String>();
        parameters.add("Api-Token", dynatraceContext.getApiKey());

        final Optional<Proxy> proxy = getProxy(context, proxyName, dynatraceUrl);
        final HTTPGenerator http = new HTTPGenerator(HTTP_GET_METHOD, dynatraceUrl, dynatraceContext.getHeaders(), parameters, proxy);

        try {
            if(traceMode){
                context.getLogger().info("Dynatrace process, get process entity:\n" + http.getRequest());
            }
            final HttpResponse httpResponse = http.execute();

            if (HttpResponseUtils.isSuccessHttpCode(httpResponse.getStatusLine().getStatusCode())) {
                final JSONObject jsonObjectresponse = getJsonResponse(httpResponse);
                if (jsonObjectresponse != null) {
                    extractHostIdsFromProcesGroupResponse(hostid,jsonObjectresponse);
                }
            } else {
                final String stringResponse = HttpResponseUtils.getStringResponse(httpResponse);
                throw new DynatraceException(httpResponse.getStatusLine().getReasonPhrase() + " - "+ dynatraceUrl + " - " + stringResponse);
            }
        } finally {
            http.closeHttpClient();
        }

        if (traceMode) {
            context.getLogger().info("Found Hosts: " + hostid);
        }

        return hostid;
    }
*/
/*
    public static Set<String> getListDependentServicesFromServiceID(final Context context, final DynatraceContext dynatraceContext, String serviceid, final Optional<String> proxyName, final boolean traceMode,final boolean usetags) throws Exception {
        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_SERVICE+"/"+serviceid;
        final MultivaluedMap<String, String> parameters = generateGetTagsParameters(dynatraceContext.getTags(),usetags); // ? tag param not defined for this API call

        parameters.add("Api-Token", dynatraceContext.getApiKey());

 //       DynatraceService dynatraceService = null;
        final Optional<Proxy> proxy = getProxy(context, proxyName, dynatraceUrl);
        final HTTPGenerator http = new HTTPGenerator(HTTP_GET_METHOD, dynatraceUrl, dynatraceContext.getHeaders(), parameters, proxy);
        final Set<String> serviceEntityIds = new HashSet<String>();
        try {
            if(traceMode){
                context.getLogger().info("Dynatrace service, get service entity:\n" + http.getRequest());
            }
            final HttpResponse httpResponse = http.execute();

            if (HttpResponseUtils.isSuccessHttpCode(httpResponse.getStatusLine().getStatusCode())) {
                final JSONObject jsonObjectResponse = getJsonResponse(httpResponse);
                if (jsonObjectResponse != null) {
                    extractServiceIdsFromResponse(serviceEntityIds, jsonObjectResponse);
                }
            } else {
                final String stringResponse = HttpResponseUtils.getStringResponse(httpResponse);
                throw new DynatraceException(httpResponse.getStatusLine().getReasonPhrase() + " - "+ dynatraceUrl + " - " + stringResponse);
            }
        } finally {
            http.closeHttpClient();
        }

        if (traceMode) {
            context.getLogger().info("Found service: " + serviceEntityIds);
        }

        return serviceEntityIds;
    }
*/
/*
    private static void extractServiceEntityIdsFromResponse(final Set<String> serviceEntityId, final JSONArray jsonArrayResponse) {
        for (int i = 0; i < jsonArrayResponse.length(); i++) {
            final JSONObject jsonApplication = jsonArrayResponse.getJSONObject(i);
            if (jsonApplication.has("entityId") && jsonApplication.has("displayName")) {
                serviceEntityId.add(jsonApplication.getString("entityId"));
            }

        }
    }
*/
/*    
    private static void extractProcessGroupIdsFromResponse(final Set<String> processgroupIds, final JSONObject jsonObject) {

        JSONObject fromRelationships=jsonObject.getJSONObject("fromRelationships");
        try {
            JSONArray processgroups = fromRelationships.getJSONArray("runsOn");
            for (int j = 0; j < processgroups.length(); j++) {
                processgroupIds.add(processgroups.getString(j));
            }
        }
        catch (JSONException e)
        {
            //----print the exeption
        }
    }
*/
    
/*    
    private static void extractServiceIdsFromResponse(final Set<String> serviceIds, final JSONObject jsonObject) {

        JSONObject fromRelationships=jsonObject.getJSONObject("fromRelationships");
        try {
            JSONArray services = fromRelationships.getJSONArray("calls");
            for (int j = 0; j < services.length(); j++) {
                serviceIds.add(services.getString(j));
            }
        }catch(JSONException e)
        {
            //----print the exeption
        }
*/
       /* JSONObject toRelationships=jsonObject.getJSONObject("toRelationships");
        try {
            JSONArray toRelationshipscalls = toRelationships.getJSONArray("calls");
            for (int i = 0; i < toRelationshipscalls.length(); i++) {
                if (toRelationshipscalls.getString(i).contains(DYNATRACE_SERVICE_PREFIX))
                    processgroupInstanceIds.add(toRelationshipscalls.getString(i));
            }
        }
        catch(JSONException e)
        {
            //----print the exeption
        }*/
/*
    }
*/
/*    
    private static String extractProcessGroupInstanceIdsFromResponse(final Set<String> processgroupInstanceIds, final JSONObject jsonObject) {

        JSONObject fromRelationships=jsonObject.getJSONObject("fromRelationships");
        JSONArray  processgroupinstances=fromRelationships.getJSONArray("runsOnProcessGroupInstance");
        String applicationName=jsonObject.getString("displayName");
        for(int j=0;j<processgroupinstances.length();j++)
        {
            processgroupInstanceIds.add(processgroupinstances.getString(j));
        }

        return applicationName;

    }
*/
    public static void updateTagOnServiceID(final Context context, final DynatraceContext dynatraceContext, final boolean traceMode,String serviceID) throws Exception {
//        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_SERVICE+"/"+serviceID;
        updateTagsOnEntity(context,dynatraceContext,traceMode,DYNATRACE_SERVICE+"/"+serviceID);
    }
    public static void updateTagOnPgInstanceID(final Context context, final DynatraceContext dynatraceContext, final boolean traceMode,String pginstanceid) throws Exception {
//        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_PROCESS+"/"+pginstanceid;
        updateTagsOnEntity(context,dynatraceContext,traceMode,DYNATRACE_PROCESS+"/"+pginstanceid);
    }
    public static void updateTagOnProcessGroupID(final Context context, final DynatraceContext dynatraceContext, final boolean traceMode,String processgroupid) throws Exception {
//        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_PROCESSGROUP+"/"+processgroupid;
        updateTagsOnEntity(context,dynatraceContext,traceMode,DYNATRACE_PROCESSGROUP+"/"+processgroupid);
    }
    public static void updateTagOnHostID(final Context context, final DynatraceContext dynatraceContext, final boolean traceMode,String hostid) throws Exception {
//        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_HOST+"/"+hostid;
        updateTagsOnEntity(context,dynatraceContext,traceMode,DYNATRACE_HOST+"/"+hostid);
    }
    
    private static void updateTagsOnEntity(final Context context, final DynatraceContext dynatraceContext, final boolean traceMode,String endpoint) throws Exception {
        String jsonpayloadstart;
        String jsonpayloadend;
        String jsonpayload;
//        final MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
        if(dynatraceContext.getTags().isPresent()) {

            jsonpayloadstart="{\n" +
                    "  \"tags\": [\n";

            jsonpayloadend ="  ]\n" +
                    "}";

            jsonpayload=jsonpayloadstart+ DynatraceTaggingUtils.convertforUpdateTags(dynatraceContext.getTags()) + jsonpayloadend;
            
            DynatraceUtils.executeDynatraceAPIPostObjectRequest(context, dynatraceContext, Api.ENV1, endpoint, jsonpayload, traceMode);
            
/*            
            parameters.add("Api-Token", dynatraceContext.getApiKey());

            final Optional<Proxy> proxy = getProxy(context, dynatraceContext.getProxyname(), url);
            final HTTPGenerator httpGenerator = new HTTPGenerator(HTTP_POST_METHOD, url,  dynatraceContext.getHeaders(), parameters, proxy,jsonpayload);
            try {
                if(traceMode){
                    context.getLogger().info("Dynatrace update Tags on entity:\n" + httpGenerator.getRequest());
                }
                final HttpResponse httpResponse = httpGenerator.execute();

                if (!HttpResponseUtils.isSuccessHttpCode(httpResponse.getStatusLine().getStatusCode())) {
                    final String stringResponse = HttpResponseUtils.getStringResponse(httpResponse);
                    throw new DynatraceException(httpResponse.getStatusLine().getReasonPhrase() + " - "+ url + " - " + stringResponse);

                }
            } finally {
                httpGenerator.closeHttpClient();
            }
*/
        }
    }

/*
    private static void updateServices(final Context context, final DynatraceContext dynatraceContext, final Optional<String> proxyName, final boolean traceMode,String serviceID) throws Exception {
        String jsonpayload;
        final String dynatraceUrl = getDynatraceEnv1ApiUrl(dynatraceContext.getDynatraceManagedHostname(), dynatraceContext.getDynatraceAccountID()) + DYNATRACE_SERVICE+"/"+serviceID;
        final MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
        if(dynatraceContext.getTags().isPresent()) {
            jsonpayload="{\n" +
                    "  \"tags\": [\n" +
                    "    \""+dynatraceContext.getTags().get()+"\"\n" +
                    "  ]\n" +
                    "}";


            parameters.add("Api-Token", dynatraceContext.getApiKey());

            final Optional<Proxy> proxy = getProxy(context, proxyName, dynatraceUrl);
            final HTTPGenerator httpGenerator = new HTTPGenerator(HTTP_POST_METHOD, dynatraceUrl,  dynatraceContext.getHeaders(), parameters, proxy,jsonpayload);
           try {
                if(traceMode){
                    context.getLogger().info("Dynatrace service, update Tags:\n" + httpGenerator.getRequest());
                }
                final HttpResponse httpResponse = httpGenerator.execute();

                if (!HttpResponseUtils.isSuccessHttpCode(httpResponse.getStatusLine().getStatusCode())) {
                    final String stringResponse = HttpResponseUtils.getStringResponse(httpResponse);
                    throw new DynatraceException(httpResponse.getStatusLine().getReasonPhrase() + " - "+ dynatraceUrl + " - " + stringResponse);

                }
            } finally {
               httpGenerator.closeHttpClient();
            }


        }
    }
*/
/*    
    private static String extractProcessGroupFromResponse(final Set<String> processgroupIds, final JSONObject jsonObject) {

        JSONObject fromRelationships=jsonObject.getJSONObject("fromRelationships");
        JSONArray  processgroupinstances=fromRelationships.getJSONArray("runsOn");
        String applicationName=jsonObject.getString("displayName");
        for(int j=0;j<processgroupinstances.length();j++)
        {
            processgroupIds.add(processgroupinstances.getString(j));
        }

        return applicationName;

    }
*/
/*    
    private static void extractSerivcesIdsFromResponse(final Set<String> serviceId, final JSONArray jsonArrayResponse,String applicationName) {
        applicationName=applicationName.trim();
        for (int i = 0; i < jsonArrayResponse.length(); i++) {
            final JSONObject jsonApplication = jsonArrayResponse.getJSONObject(i);
            if (jsonApplication.has("entityId") && jsonApplication.has("displayName")) {
                if(jsonApplication.getString("displayName").equalsIgnoreCase(applicationName)) {
                    JSONObject fromRelationships=jsonApplication.getJSONObject("fromRelationships");
                    JSONArray  servicesList=fromRelationships.getJSONArray("calls");
                    for(int j=0;j<servicesList.length();j++)
                    {
                        serviceId.add(servicesList.getString(j));
                    }
                }
            }
        }
    }
*/
/*    
    private static void extractHostIdsFromProcesGroupResponse(final Set<String> hostId, final JSONObject jsonObjectResponse) {
        JSONObject fromRelationships=jsonObjectResponse.getJSONObject("fromRelationships");
        JSONArray  processOF=fromRelationships.getJSONArray("runsOn");

        for(int j=0;j<processOF.length();j++)
        {
            hostId.add(processOF.getString(j));
        }

    }
*/
/*    
    private static void extractHostIdsFromResponse(final Set<String> hostId, final JSONObject jsonObjectResponse) {
        JSONObject fromRelationships=jsonObjectResponse.getJSONObject("fromRelationships");
        JSONArray  processOF=fromRelationships.getJSONArray("isProcessOf");

        for(int j=0;j<processOF.length();j++)
        {
            hostId.add(processOF.getString(j));
        }

    }
*/
    public static Optional<Proxy> getProxy(final Context context, final Optional<String> proxyName, final String url) throws MalformedURLException {
        if (proxyName.isPresent()) {
            return Optional.fromNullable(context.getProxyByName(proxyName.get(), new URL(url)));
        }
        return Optional.absent();
    }

    public static Optional<Proxy> getNeoLoadWebProxy(final Context context, final String url) throws MalformedURLException {
       return Optional.fromNullable(context.getProxyByType(ProxyType.NEOLOAD_WEB, new URL(url)));
    }

    
    public static String getDynatraceAnyApiUrl(final Optional<String> dynatraceManagedHostname, final String dynatraceAccountID, Api api) {
		switch (api) {
		case ENV1:
			return getDynatraceEnv1ApiUrl(dynatraceManagedHostname,dynatraceAccountID);
		case ENV2:
			return getDynatraceEnv2ApiUrl(dynatraceManagedHostname,dynatraceAccountID);
		case CFG:
		default:
			return getDynatraceConfigApiUrl(dynatraceManagedHostname,dynatraceAccountID);
		}
    }
    
    public static String getDynatraceEnv1ApiUrl(final Optional<String> dynatraceManagedHostname, final String dynatraceAccountID) {
        if (dynatraceManagedHostname.isPresent()) {
            return DYNATRACE_PROTOCOL + dynatraceManagedHostname.get() + "/e/" + dynatraceAccountID + "/api/v1/";
        } else {
            return DYNATRACE_PROTOCOL + dynatraceAccountID + DYNATRACE_ENV1URL;
        }
    }

    public static String getDynatraceEnv2ApiUrl(final Optional<String> dynatraceManagedHostname, final String dynatraceAccountID) {
        if (dynatraceManagedHostname.isPresent()) {
            return DYNATRACE_PROTOCOL + dynatraceManagedHostname.get() + "/e/" + dynatraceAccountID + "/api/v2/";
        } else {
            return DYNATRACE_PROTOCOL + dynatraceAccountID + DYNATRACE_ENV2URL;
        }
    }

    public static String getDynatraceConfigApiUrl(final Optional<String> dynatraceManagedHostname, final String dynatraceAccountID) {
        if (dynatraceManagedHostname.isPresent()) {
            return DYNATRACE_PROTOCOL + dynatraceManagedHostname.get() + "/e/" + dynatraceAccountID + "/api/config/v1/";
        } else {
            return DYNATRACE_PROTOCOL + dynatraceAccountID + DYNATRACE_CONFIGURL;
        }
    }
    
    public static JSONArray executeDynatraceAPIGetArrayRequest(Context context, DynatraceContext dynatracecontext, Api api, String endpoint, MultivaluedMap<String,String> params, boolean tracemode) throws Exception {
		final String dynatraceurl=getDynatraceAnyApiUrl(dynatracecontext.getDynatraceManagedHostname(),dynatracecontext.getDynatraceAccountID(),api) + endpoint;
//		params.put("Api-Token", dynatracecontext.getApiKey());	
		Map<String,String> headers=new HashMap<>();
		headers.put("Authorization","Api-Token "+dynatracecontext.getApiKey());
        headers.put("Accept","application/json; charset=utf-8");
    	
		final Optional<Proxy> proxy = getProxy(context, dynatracecontext.getProxyname(), dynatraceurl);
		final HTTPGenerator http = new HTTPGenerator(HTTP_GET_METHOD, dynatraceurl, headers, params, proxy);
		
		try {
			if (tracemode) {
				context.getLogger().info("Dynatrace GET request:\n" + http.getRequest());
			}
			final HttpResponse httpresponse = http.execute();
			
			if (HttpResponseUtils.isSuccessHttpCode(httpresponse.getStatusLine().getStatusCode())) {
				return getJsonArrayResponse(httpresponse);
			} else {
				final String stringResponse = HttpResponseUtils.getStringResponse(httpresponse);
				throw new DynatraceException(
					httpresponse.getStatusLine().getReasonPhrase() + " - " + dynatraceurl + " - " + stringResponse);
			}
		} finally {
			http.closeHttpClient();
		}
    }

    public static JSONObject executeDynatraceAPIGetObjectRequest(Context context, DynatraceContext dynatracecontext, Api api , String endpoint, MultivaluedMap<String,String> params, boolean tracemode) throws Exception {
		final String dynatraceurl = getDynatraceAnyApiUrl(dynatracecontext.getDynatraceManagedHostname(),dynatracecontext.getDynatraceAccountID(),api) + endpoint;
//		params.put("Api-Token", dynatracecontext.getApiKey());
		Map<String,String> headers=new HashMap<>();
		headers.put("Authorization","Api-Token "+dynatracecontext.getApiKey());
        headers.put("Accept","application/json; charset=utf-8");
   	
		final Optional<Proxy> proxy = getProxy(context, dynatracecontext.getProxyname(), dynatraceurl);
		final HTTPGenerator http = new HTTPGenerator(HTTP_GET_METHOD, dynatraceurl, headers, params, proxy);
		
		try {
			if (tracemode) {
				context.getLogger().info("Dynatrace GET request:\n" + http.getRequest());
			}
			final HttpResponse httpresponse = http.execute();
			
			if (HttpResponseUtils.isSuccessHttpCode(httpresponse.getStatusLine().getStatusCode())) {
				return getJsonResponse(httpresponse);
			} else {
				final String stringResponse = HttpResponseUtils.getStringResponse(httpresponse);
				throw new DynatraceException(
					httpresponse.getStatusLine().getReasonPhrase() + " - " + dynatraceurl + " - " + stringResponse);
			}
		} finally {
			http.closeHttpClient();
		}
    }

    public static JSONObject executeDynatraceAPIPostObjectRequest(Context context, DynatraceContext dynatracecontext, Api api, String endpoint, String payload, boolean tracemode) throws Exception {
		final String dynatraceurl = getDynatraceAnyApiUrl(dynatracecontext.getDynatraceManagedHostname(),dynatracecontext.getDynatraceAccountID(),api) + endpoint;
		MultivaluedMap<String,String> params=new MultivaluedHashMap<>();
		Map<String,String> headers=new HashMap<>();
		headers.put("Authorization","Api-Token "+dynatracecontext.getApiKey());
        headers.put("Accept","application/json; charset=utf-8");
		headers.put("Content-Type", "application/json");
   	
		final Optional<Proxy> proxy = getProxy(context, dynatracecontext.getProxyname(), dynatraceurl);
		final HTTPGenerator http = new HTTPGenerator(HTTP_POST_METHOD, dynatraceurl, headers, params, proxy,payload);
		
		try {
			if (tracemode) {
				context.getLogger().info("Dynatrace POST request:\n" + http.getRequest());
			}
			final HttpResponse httpresponse = http.execute();
			
			if (HttpResponseUtils.isSuccessHttpCode(httpresponse.getStatusLine().getStatusCode())) {
				return getJsonResponse(httpresponse);
			} else {
				final String stringResponse = HttpResponseUtils.getStringResponse(httpresponse);
				throw new DynatraceException(
					httpresponse.getStatusLine().getReasonPhrase() + " - " + dynatraceurl + " - " + stringResponse);
			}
		} finally {
			http.closeHttpClient();
		}
    }

    public static void executeDynatraceAPIDeleteRequest(Context context, DynatraceContext dynatracecontext, Api api, String endpoint, boolean tracemode) throws Exception {
		final String dynatraceurl = getDynatraceAnyApiUrl(dynatracecontext.getDynatraceManagedHostname(),dynatracecontext.getDynatraceAccountID(),api) + endpoint;
		MultivaluedMap<String,String> params=new MultivaluedHashMap<>();
		Map<String,String> headers=new HashMap<>();
		headers.put("Authorization","Api-Token "+dynatracecontext.getApiKey());
//      headers.put("Accept","application/json; charset=utf-8");
//		headers.put("Content-Type", "application/json");
   	
		final Optional<Proxy> proxy = getProxy(context, dynatracecontext.getProxyname(), dynatraceurl);
		final HTTPGenerator http = new HTTPGenerator(HTTP_DELETE_METHOD, dynatraceurl, headers, params, proxy);
		
		try {
			if (tracemode) {
				context.getLogger().info("Dynatrace DELETE request:\n" + http.getRequest());
			}
			final HttpResponse httpresponse = http.execute();
			
			if (HttpResponseUtils.isSuccessHttpCode(httpresponse.getStatusLine().getStatusCode())) {
                if(tracemode)
                    context.getLogger().info("Dynatrace object properly deleted");
			} else {
				final String stringResponse = HttpResponseUtils.getStringResponse(httpresponse);
				throw new DynatraceException(
					httpresponse.getStatusLine().getReasonPhrase() + " - " + dynatraceurl + " - " + stringResponse);
			}
		} finally {
			http.closeHttpClient();
		}
    }
    
    public static int executeDynatraceAPIPutRequest(Context context, DynatraceContext dynatracecontext, Api api, String endpoint, String payload, boolean tracemode) throws Exception {
		final String dynatraceurl = getDynatraceAnyApiUrl(dynatracecontext.getDynatraceManagedHostname(),dynatracecontext.getDynatraceAccountID(),api) + endpoint;
		MultivaluedMap<String,String> params=new MultivaluedHashMap<>();
		Map<String,String> headers=new HashMap<>();
		headers.put("Authorization","Api-Token "+dynatracecontext.getApiKey());
//      headers.put("Accept","application/json; charset=utf-8");
		headers.put("Content-Type", "application/json");
   	
		final Optional<Proxy> proxy = getProxy(context, dynatracecontext.getProxyname(), dynatraceurl);
		final HTTPGenerator http = new HTTPGenerator(HTTP_PUT_METHOD, dynatraceurl, headers, params, proxy, payload);
//		final HTTPGenerator http = new HTTPGenerator(HTTP_PUT_METHOD, dynatraceurl, headers, params, proxy);
		
		try {
			if (tracemode) {
				context.getLogger().info("Dynatrace PUT request:\n" + http.getRequest());
			}
			final HttpResponse httpresponse = http.execute();
			int statuscode=httpresponse.getStatusLine().getStatusCode();
			if (HttpResponseUtils.isSuccessHttpCode(statuscode)) {
                if(tracemode)
                    context.getLogger().info("Dynatrace data properly put");
                return statuscode;
			} else {
				final String stringResponse = HttpResponseUtils.getStringResponse(httpresponse);
				throw new DynatraceException(
					httpresponse.getStatusLine().getReasonPhrase() + " - " + dynatraceurl + " - " + stringResponse);
			}
		} finally {
			http.closeHttpClient();
		}
    }
    
}
