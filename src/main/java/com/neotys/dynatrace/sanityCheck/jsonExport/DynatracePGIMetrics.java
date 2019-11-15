package com.neotys.dynatrace.sanityCheck.jsonExport;

import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.neotys.dynatrace.common.DynatraceContext;
import com.neotys.dynatrace.common.DynatraceException;
import com.neotys.dynatrace.common.DynatraceUtils;
import com.neotys.dynatrace.common.data.DynatraceServiceData;
import com.neotys.dynatrace.common.topology.DynatraceTopologyCache;
import com.neotys.dynatrace.common.topology.DynatraceTopologyWalker;
import com.neotys.dynatrace.common.data.DynatraceService;
import com.neotys.dynatrace.monitoring.timeseries.DynatraceMetric;
import com.neotys.extensions.action.engine.Context;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class DynatracePGIMetrics {
    private static final Map<String,String> PGI_TIMESERIES_MAP=new HashMap<>();
    private static final long DYNATRACE_SMARTSCAPE_DIFF=300000;
    private static final Optional<Long> diff=Optional.of(DYNATRACE_SMARTSCAPE_DIFF);
    private final Context context;
    private final DynatraceContext dynatracecontext;
    private final DynatraceTopologyWalker dtw;
    private long startTS;
    private boolean traceMode;
    private String applicationName;
    private final static double COMPARAISON_RATIO=0.10;

    static {
        //----moinitoring of the entire services ( logic based on process group instance)
        PGI_TIMESERIES_MAP.put("com.dynatrace.builtin:pgi.cpu.usage", "AVG");
        PGI_TIMESERIES_MAP.put("com.dynatrace.builtin:pgi.mem.usage", "AVG");
        PGI_TIMESERIES_MAP.put("com.dynatrace.builtin:pgi.nic.bytesreceived", "AVG");
        PGI_TIMESERIES_MAP.put("com.dynatrace.builtin:pgi.nic.bytessent", "AVG");
        //----------------------------------------------------------------------------------
    }
 
    public DynatracePGIMetrics( String dynatraceApiKey, String dynatraceId, final Optional<String> dynatraceTags, Optional<String> dynatraceManagedHostname,Optional<String> proxyName, Context context,long startTS, boolean traceMode) throws Exception {
    	this.dynatracecontext=new DynatraceContext(dynatraceApiKey,dynatraceManagedHostname,dynatraceId,proxyName, dynatraceTags);
    	
        this.context = context;
        this.traceMode = traceMode;
        this.startTS=startTS;
        
        this.dtw=new DynatraceTopologyWalker(context, dynatracecontext, traceMode);
        dtw.executeDiscovery();

        if(dynatraceTags.isPresent())
            applicationName=dynatraceTags.get();
        else
            applicationName="DYNATRACE";

    }

    public void marshal(String filename , List<DynatraceServiceData> dynatraceServiceDataList) throws  IOException {
        DynatraceSmartScapedata smartScapedata = new DynatraceSmartScapedata(applicationName,dynatraceServiceDataList);
        Writer writer = new FileWriter(filename);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String strJson = gson.toJson(smartScapedata);
        context.getLogger().info(strJson);
        writer.write(strJson);
        writer.close();

    }

    public void sanityCheck(String jsonfilename) throws IOException, DynatraceException {
        List<DynatraceServiceData> serviceDataList=getSmartscapeData();
        File jsonfile=new File(jsonfilename);


        if(jsonfile.exists()) {
            //----compare with baseline------
            if(jsonfilename.contains(".json")) {
                DynatraceSmartScapedata imported_architecture=unmarshall(jsonfilename);
                if(imported_architecture.getServiceDataList().size()>serviceDataList.size()) {
                    context.getLogger().error("There is less services running on the application");
                    throw new DynatraceException("There is less services running on the application");
                } else {
                    List<String> listoferrors=new ArrayList<>();
                    imported_architecture.getServiceDataList().stream().forEach(services->{
                        DynatraceServiceData relatedata=getDynatraceServiceData(services,serviceDataList);
                        if(relatedata!=null) {
                            if(services.getNumber_ofprocess()>relatedata.getNumber_ofprocess()) {
                                context.getLogger().error("There are less process running on " + services.getServiceName());
                                listoferrors.add("There are less process running on " + services.getServiceName());
                            } else {
                                //------check the resources----------------
                                if(!compareMonitoringData(services.getCpu(),relatedata.getCpu())) {
                                    context.getLogger().error("The process are consuming more CPU"+services.getServiceName());
                                    listoferrors.add("There are less process running on " + services.getServiceName());
                                } else {
                                    if(!compareMonitoringData(services.getMemory(),relatedata.getMemory())) {
                                        context.getLogger().error("The process are consuming more Memory"+services.getServiceName());
                                        listoferrors.add("There are less process running on " + services.getServiceName());
                                    }
                                }
                            }
                        } else {
                            context.getLogger().error("Service is missing "+services.getServiceName());
                        }
                    });
                    if(listoferrors.size()==0) {
                        //----if no error then store the new reference in the json file
                        marshal(jsonfilename,serviceDataList);
                    } else {
                        throw new DynatraceException(listoferrors.stream().limit(1).collect(Collectors.joining(",")));
                    }
                }
            }
        } else {
            //--- export current data to json ----
            marshal(jsonfilename,serviceDataList);
        }
    }

	private boolean compareMonitoringData(double oldvalue, double newValue) {
//		double minvalue = oldvalue - oldvalue * COMPARAISON_RATIO;
		double maxvalue = oldvalue + oldvalue * COMPARAISON_RATIO;

		if (newValue <= maxvalue)
			return true;
		else
			return false;
	}

    private DynatraceServiceData getDynatraceServiceData(DynatraceServiceData data, List<DynatraceServiceData> serviceDataList) {
        java.util.Optional<DynatraceServiceData> findservicedata=serviceDataList.stream().filter(dynatraceServiceData ->( dynatraceServiceData.getServiceName().equals(data.getServiceName())||dynatraceServiceData.getServiceID().equals(data.getServiceID()))).findFirst();
        if(findservicedata.isPresent())
            return findservicedata.get();
        else
            return null;
    }
    
    public DynatraceSmartScapedata unmarshall(String filename) throws FileNotFoundException {
        Gson gson = new Gson();
        JsonReader reader = new JsonReader(new FileReader(filename));
        return (DynatraceSmartScapedata) gson.fromJson(reader, DynatraceSmartScapedata.class);
    }

    public List<DynatraceServiceData> getSmartscapeData() {
        List<DynatraceServiceData> dynatraceServiceDataList=new ArrayList<>();
        DynatraceTopologyCache dtcache=dtw.getDiscoveredData();

        dynatraceServiceDataList=dtcache.getServices().stream().map((serviceid) -> {
            try {
        		return new DynatraceService(serviceid,dtcache.lookupServiceName(serviceid),dtw.findPgInstancesForService(serviceid));
            } catch (Exception e) {
                return null;
            }
        } ).filter(Objects::nonNull).map(dynatraceService -> getServiceMonitoringData(dynatraceService)).filter(Objects::nonNull).collect(Collectors.toList());

        return dynatraceServiceDataList;
    }

    private DynatraceServiceData getServiceMonitoringData(DynatraceService dynatraceService) {
        DynatraceServiceData data=new DynatraceServiceData(dynatraceService.getDisplayName(),dynatraceService.getServiceid(),dynatraceService.getNumber_ofprocess());
        try {
            for (Map.Entry<String, String> m : PGI_TIMESERIES_MAP.entrySet()) {
                List<DynatraceMetric> dynatraceMetrics = (List<DynatraceMetric>) DynatraceUtils.getTimeSeriesMetricData(m.getKey(), m.getValue(), dynatraceService.getProcessPGIlist(), startTS, context, dynatracecontext, traceMode, diff,Optional.of(true));
                double total = dynatraceMetrics.stream().mapToDouble(metric->metric.getValue()).sum();
                addSumTodata(data,m.getKey(),total);
            }
            return data;
        } catch (Exception e) {
            context.getLogger().error("Technical Error retrieving monitoring ",e);
        }
        return null;
    }

    private void addSumTodata(DynatraceServiceData data,String metricname,double sum){

        if(metricname.contains("cpu"))
            data.setCpu(sum);

        if(metricname.contains("mem"))
            data.setMemory(sum);

        if(metricname.contains("bytesreceived"))
            data.setNetworkreceived(sum);

        if(metricname.contains("bytessent"))
            data.setNetworksent(sum);
    }
}
