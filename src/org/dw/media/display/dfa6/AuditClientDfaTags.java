package org.dw.media.display.dfa6;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.api.ads.dfa.axis.factory.DfaServices;
import com.google.api.ads.dfa.axis.v1_19.ActiveFilter;
import com.google.api.ads.dfa.axis.v1_19.Advertiser;
import com.google.api.ads.dfa.axis.v1_19.AdvertiserRecordSet;
import com.google.api.ads.dfa.axis.v1_19.AdvertiserRemote;
import com.google.api.ads.dfa.axis.v1_19.AdvertiserSearchCriteria;
import com.google.api.ads.dfa.axis.v1_19.ApiException;
import com.google.api.ads.dfa.axis.v1_19.ChangeLogRecord;
import com.google.api.ads.dfa.axis.v1_19.ChangeLogRecordSearchCriteria;
import com.google.api.ads.dfa.axis.v1_19.ChangeLogRecordSet;
import com.google.api.ads.dfa.axis.v1_19.ChangeLogRemote;
import com.google.api.ads.dfa.axis.v1_19.CustomSpotlightVariable;
import com.google.api.ads.dfa.axis.v1_19.CustomSpotlightVariableConfiguration;
import com.google.api.ads.dfa.axis.v1_19.FloodlightPublisherTag;
import com.google.api.ads.dfa.axis.v1_19.FloodlightTag;
import com.google.api.ads.dfa.axis.v1_19.SpotlightActivity;
import com.google.api.ads.dfa.axis.v1_19.SpotlightActivityGroup;
import com.google.api.ads.dfa.axis.v1_19.SpotlightActivityGroupRecordSet;
import com.google.api.ads.dfa.axis.v1_19.SpotlightActivityGroupSearchCriteria;
import com.google.api.ads.dfa.axis.v1_19.SpotlightActivityRecordSet;
import com.google.api.ads.dfa.axis.v1_19.SpotlightActivitySearchCriteria;
import com.google.api.ads.dfa.axis.v1_19.SpotlightActivityType;
import com.google.api.ads.dfa.axis.v1_19.SpotlightConfiguration;
import com.google.api.ads.dfa.axis.v1_19.SpotlightRemote;
import com.google.api.ads.dfa.lib.client.DfaSession;

/**
 * This class pulls spotlight tag data, default tag data, or 
 * publisher tag data for a specified advertiser from DFA. It is
 * not pretty, but it gets the job done.
 *
 * Uses the DFA Axis v19 API, which is soon (as of the time of this
 * writing) to be deprecated. Will almost certainly need to be updated
 * to the newest version, which is the DCM/DFA Reporting and Trafficking
 * API version 2.0. See Release Notes at
 * https://developers.google.com/doubleclick-advertisers/reporting/rel_notes
 * 
 * Values for all five args are always required:
 * arg0 - username (user profile - not a Google Account username)
 * arg1 - password (user profile password - not a Google Account password)
 * arg2 - DFA Advertiser ID
 * arg3 - tags type to retrieve data for (1 - Spotlight Tags, 2 - Default (dynamic) Tags, 
 *	      3 - Publisher Tags, 4 - All Tag Types)
 * arg4 - file name to output data to
 * 
 * TODO:
 * add data-pass (%p) checks
 * add secure-compliance checks (https floodlight to http pixel)
 * add routine to find pixel redirects
 * add checks to pixel redirects
 */
public class AuditClientDfaTags {
	
	private static File file;
	private static FileWriter fw;
	private static BufferedWriter bw;
	
	private static HashMap<Long, ChangeLogRecord> floodlightTagRecords = new HashMap<Long, ChangeLogRecord>();

  public static void main(String[] args) throws Exception {
	  if (checkArgs(args) != true) {
		  System.out.println("Run failure.");
		  System.exit(1);
	  } else {
	  
	    // Construct a DfaSession.
	    DfaSession session = new DfaSession.Builder()
	        //.fromFile()
	    	.withUsernameAndPassword(args[0], args[1])
	    	.withEnvironment(DfaSession.Environment.PRODUCTION)
	    	.withApplicationName("AuditClientDfaTags")
	        .build();
	
	    DfaServices dfaServices = new DfaServices();
	    
	    long advertiserId = Long.valueOf(args[2]).longValue();
	
	    int tagType = Integer.parseInt(args[3].toString());
	    
	    switch (tagType) {
	    
	    	// spotlight tags
	    	case 1:
	    		createFile(args[4]);
	    		getSpotlightTags(dfaServices, session, advertiserId);
	    		bw.close();
	    	    fw.close();
	    		break;
	    		
	    	// default tags / dynamic pixels
	    	case 2:
	    		createFile(args[4]);
	    		getDefaultTags(dfaServices, session, advertiserId);
	    		bw.close();
	    	    fw.close();
	    		break;
	    		
	    	// publisher tags
	    	case 3:
	    		createFile(args[4]);
	    		getPublisherTags(dfaServices, session, advertiserId);
	    		bw.close();
	    	    fw.close();
	    		break;
	    	
	    	// all tag types (ignores file name supplied as parameter 4)
	    	case 4:
	    		createFile("spotlight_tags.txt");
	    		getSpotlightTags(dfaServices, session, advertiserId);
	    		bw.close();
	    	    fw.close();
	    		
	    		createFile("default_tags.txt");
	    		getDefaultTags(dfaServices, session, advertiserId);
	    		bw.close();
	    	    fw.close();
	    		
	    		createFile("publisher_tags.txt");
	    		getPublisherTags(dfaServices, session, advertiserId);
	    		bw.close();
	    	    fw.close();
	    		
	    		break;
	    		
	    }
	    
	    System.out.println("Process complete.");
	    System.exit(0);
	  }	
  }

  private static void getSpotlightTags(DfaServices dfaServices, DfaSession session, long advertiserId) throws Exception {
	/*
	 * Get and print advertiser data
	 */
	AdvertiserRemote advService = dfaServices.get(session, AdvertiserRemote.class);
	AdvertiserSearchCriteria advSearchCriteria = setAdvertiserSearchCriteria(advertiserId);
	AdvertiserRecordSet advRecordSet = advService.getAdvertisers(advSearchCriteria);
	printAdvertiserInfo(advRecordSet);
	
	/*
	 * Get spotlight data
	 */
    // Request the Spotlight service
    SpotlightRemote spotService = dfaServices.get(session, SpotlightRemote.class);
    
    // Create spotlight search criteria structure
    SpotlightActivitySearchCriteria spotActiveSearchCriteria = setSpotlightActivitySearchCriteria(advertiserId, true); // active (not archived) spotlight activities
    SpotlightActivitySearchCriteria spotInactiveSearchCriteria = setSpotlightActivitySearchCriteria(advertiserId, false); // inactive (archived) spotlight activities
    
    // Get custom spotlight variable types
    String[] customSpotlightVariableTypes = new String[3];
    customSpotlightVariableTypes[1] = "String";
    customSpotlightVariableTypes[2] = "Numeric";
    
    // Get spotlight activity types
    String[] spotlightActivityTypes = new String[3];
    spotlightActivityTypes[1] = "Counter";
    spotlightActivityTypes[2] = "Sales";
    
    // Get spotlight activity method types
    String[] spotlightActivityMethodTypes = new String[6];
    spotlightActivityMethodTypes[1] = "Standard";
    spotlightActivityMethodTypes[2] = "Unique";
    spotlightActivityMethodTypes[3] = "Per Session";
    spotlightActivityMethodTypes[4] = "Transactions";
    spotlightActivityMethodTypes[5] = "Items Sold";
    
    // Get spotlight activity groups for this advertiser
    HashMap<Long, SpotlightActivityGroup> spotlightActivityGroups = new HashMap<Long, SpotlightActivityGroup>(); // store in HashMap
    SpotlightActivityGroupSearchCriteria spotActGrpSearchCriteria = new SpotlightActivityGroupSearchCriteria();
    spotActGrpSearchCriteria.setAdvertiserId(advertiserId);
    SpotlightActivityGroupRecordSet spotActGrpRecordSet = spotService.getSpotlightActivityGroups(spotActGrpSearchCriteria);
    for (SpotlightActivityGroup spotActGrp : spotActGrpRecordSet.getRecords()) {
    	spotlightActivityGroups.put(spotActGrp.getId(), spotActGrp);
    }
    
    // Get and print custom spotlight variables established for this advertiser
    SpotlightConfiguration spotConfig = spotService.getSpotlightConfiguration(advertiserId);
    CustomSpotlightVariableConfiguration[] spotCustomVarConfs = spotConfig.getCustomSpotlightVariableConfigurations();
    TreeMap<Long, CustomSpotlightVariableConfiguration> spotCustomVarConfigs = new TreeMap<Long, CustomSpotlightVariableConfiguration>(); // store in TreeMap (sortable)
    
    out("Variable\tFriendlyName\tType\n", bw);
    for (CustomSpotlightVariableConfiguration csvc:spotCustomVarConfs) {
    	spotCustomVarConfigs.put(csvc.getId(), csvc);
    }
    for (Map.Entry<Long, CustomSpotlightVariableConfiguration> csvc : spotCustomVarConfigs.entrySet()) {
    	out("u"+csvc.getValue().getId()+"\t"+csvc.getValue().getName()+"\t"+customSpotlightVariableTypes[csvc.getValue().getType()]+"\n", bw);
    }
    out("\n", bw);   
    
    out("Spotlight Tag ID\t", bw);
    out("Spotlight Tag Name\t", bw);
    out("Is Archived?\t", bw);
    out("Activity Group\t", bw);
    out("Expected URL\t", bw);
    out("Is Spotlight Secure?\t", bw);
    out("Tag Type\t", bw);
    out("Counting Method\t", bw);
    for (Map.Entry<Long, CustomSpotlightVariableConfiguration> csvc : spotCustomVarConfigs.entrySet()) {
    	out("u"+csvc.getValue().getId()+":"+csvc.getValue().getName()+"\t", bw);
    }
    out("Tag Code\t", bw);
    out("\n", bw);
    
    SpotlightActivityRecordSet spotRecordSet;
    
    // Get active (not archived) spotlight activities generated for this advertiser
    do {
    	spotRecordSet = spotService.getSpotlightActivities(spotActiveSearchCriteria);

    	for (SpotlightActivity activity : spotRecordSet.getRecords()) {
    		// get Spotlight Activities
    		ArrayList<CustomSpotlightVariableConfiguration> variables = new ArrayList<CustomSpotlightVariableConfiguration>(); // arraylist for spotlight tag's custom variable configuration
    		
    		// if there are custom spotlight variables selected for this tag, put those, in order, into the variables array
    		if (activity.getAssignedCustomSpotlightVariableIds() != null) {
    			// for each custom spotlight variable in this floodlight configuration
    			for (Map.Entry<Long, CustomSpotlightVariableConfiguration> csvc : spotCustomVarConfigs.entrySet()) {
    				Boolean flag = false;
    				// for each custom spotlight variable selected for this tag
    				for (Long id: activity.getAssignedCustomSpotlightVariableIds()) {
    					if (id == csvc.getValue().getId()) {
    						flag = true;
    						variables.add(csvc.getValue());
    					}
    				}
    				if (flag == false) {
    					CustomSpotlightVariableConfiguration bcvc = new CustomSpotlightVariableConfiguration();
    					bcvc.setName(csvc.getValue().getName());
    					bcvc.setId(0);
    					variables.add(bcvc);
    				}
    			}
    		} else {
    			for (Map.Entry<Long, CustomSpotlightVariableConfiguration> csvc : spotCustomVarConfigs.entrySet()) {
    				CustomSpotlightVariableConfiguration bcvc = new CustomSpotlightVariableConfiguration();
					bcvc.setName(csvc.getValue().getName());
					bcvc.setId(0);
					variables.add(bcvc);
    			}
    		}
    		
    		out(activity.getId()+"\t", bw); // spotlight tag ID
    		out(activity.getName()+"\t", bw); // spotlight tag name
    		out("FALSE\t", bw); // spotlight tag archive status
    		out(spotlightActivityGroups.get(activity.getActivityGroupId()).getName()+"\t", bw); // spotlight tag group
    		out(activity.getExpectedUrl()+"\t", bw); // spotlight tag expected URL
    		out(activity.isSecure()+"\t", bw); // spotlight tag secure status
    		out(spotlightActivityTypes[(int) activity.getActivityTypeId()]+"\t", bw); // spotlight tag type (counter/sales)
    		out(spotlightActivityMethodTypes[(int)activity.getTagMethodTypeId()] +"\t", bw); // spotlight counting method
    		for (CustomSpotlightVariableConfiguration var : variables) { // spotlight tag custom variables
    			if (var.getId() == 0) {
    				out("off"+"\t", bw);
    			} else {
    				out("on"+"\t", bw);
    			}
    		}
    		// retrieve spotlight tag code
    		long[] tagId = new long[1];
    		tagId[0] = activity.getId();
    		String spotlightTagCode = spotService.generateTags(tagId);
    		spotlightTagCode = spotlightTagCode.replaceAll("\\\n", "");
    		out(spotlightTagCode+"\t\n", bw);

    	}
    	spotActiveSearchCriteria.setPageNumber(spotActiveSearchCriteria.getPageNumber()+1);
    } while (spotActiveSearchCriteria.getPageNumber() <= spotRecordSet.getTotalNumberOfPages());
  
    // Get inactive (archived) spotlight activities generated for this advertiser
    do {
    	spotRecordSet = spotService.getSpotlightActivities(spotInactiveSearchCriteria);

    	for (SpotlightActivity activity : spotRecordSet.getRecords()) {
    		// get Spotlight Activities
    		ArrayList<CustomSpotlightVariableConfiguration> variables = new ArrayList<CustomSpotlightVariableConfiguration>(); // arraylist for spotlight tag's custom variable configuration
    		
    		// if there are custom spotlight variables selected for this tag, put those, in order, into the variables array
    		if (activity.getAssignedCustomSpotlightVariableIds() != null) {
    			// for each custom spotlight variable in this floodlight configuration
    			for (Map.Entry<Long, CustomSpotlightVariableConfiguration> csvc : spotCustomVarConfigs.entrySet()) {
    				Boolean flag = false;
    				// for each custom spotlight variable selected for this tag
    				for (Long id: activity.getAssignedCustomSpotlightVariableIds()) {
    					if (id == csvc.getValue().getId()) {
    						flag = true;
    						variables.add(csvc.getValue());
    					}
    				}
    				if (flag == false) {
    					CustomSpotlightVariableConfiguration bcvc = new CustomSpotlightVariableConfiguration();
    					bcvc.setName(csvc.getValue().getName());
    					bcvc.setId(0);
    					variables.add(bcvc);
    				}
    			}
    		} else {
    			for (Map.Entry<Long, CustomSpotlightVariableConfiguration> csvc : spotCustomVarConfigs.entrySet()) {
    				CustomSpotlightVariableConfiguration bcvc = new CustomSpotlightVariableConfiguration();
					bcvc.setName(csvc.getValue().getName());
					bcvc.setId(0);
					variables.add(bcvc);
    			}
    		}
    		
    		out(activity.getId()+"\t", bw); // spotlight tag ID
    		out(activity.getName()+"\t", bw); // spotlight tag name
    		out("TRUE\t", bw); // spotlight tag archive status
    		out(spotlightActivityGroups.get(activity.getActivityGroupId()).getName()+"\t", bw); // spotlight tag group
    		out(activity.getExpectedUrl()+"\t", bw); // spotlight tag expected URL
    		out(activity.isSecure()+"\t", bw); // spotlight tag secure status
    		out(spotlightActivityTypes[(int) activity.getActivityTypeId()]+"\t", bw); // spotlight tag type (counter/sales)
    		out(spotlightActivityMethodTypes[(int)activity.getTagMethodTypeId()] +"\t", bw); // spotlight counting method
    		for (CustomSpotlightVariableConfiguration var : variables) { // spotlight tag custom variables
    			if (var.getId() == 0) {
    				//System.out.print("off"+"\t");
    				out("off"+"\t", bw);
    			} else {
    				//System.out.print("on"+"\t");
    				out("on"+"\t", bw);
    			}
    		}
    		// retrieve spotlight tag code
    		long[] tagId = new long[1];
    		tagId[0] = activity.getId();
    		String spotlightTagCode = spotService.generateTags(tagId);
    		spotlightTagCode = spotlightTagCode.replaceAll("\\\n", "");
    		out(spotlightTagCode+"\t\n", bw);

    	}
    	spotInactiveSearchCriteria.setPageNumber(spotInactiveSearchCriteria.getPageNumber()+1);
    } while (spotInactiveSearchCriteria.getPageNumber() <= spotRecordSet.getTotalNumberOfPages());
    
  }
  
  private static void getDefaultTags(DfaServices dfaServices, DfaSession session, long advertiserId) throws Exception {
	/*
	 * Get and print advertiser data
	 */
	AdvertiserRemote advService = dfaServices.get(session, AdvertiserRemote.class);
	AdvertiserSearchCriteria advSearchCriteria = setAdvertiserSearchCriteria(advertiserId);
	AdvertiserRecordSet advRecordSet = advService.getAdvertisers(advSearchCriteria);
	printAdvertiserInfo(advRecordSet);
	
	// Print out default tags headers
	out("Spotlight Tag ID\t", bw);
	out("Spotlight Tag Name\t", bw);
	out("Is Spotlight Secure?\t", bw);
	out("Vendor\t", bw);
	out("Vendor Pixel ID\t", bw);
	out("Vendor Description\t", bw);
	out("Date Added\t", bw);
	out("Vendor Pixel Code\t", bw);
	out("\n", bw);
  
	/*
	 * Get spotlight data
	 */
    // Request the Spotlight service
    SpotlightRemote spotService = dfaServices.get(session, SpotlightRemote.class);
    
    // Create spotlight search criteria structure
    SpotlightActivitySearchCriteria spotSearchCriteria = setSpotlightActivitySearchCriteria(advertiserId);
    SpotlightActivityRecordSet spotRecordSet;
    
    // Request the ChangeLogRecord service
    ChangeLogRemote logService = dfaServices.get(session, ChangeLogRemote.class);
    
    // Get spotlight activities generated for this advertiser
    do {
    	spotRecordSet = spotService.getSpotlightActivities(spotSearchCriteria);

    	for (SpotlightActivity activity : spotRecordSet.getRecords()) {
    		
    		// Create change log search criteria structure
    	    ChangeLogRecordSearchCriteria changeLogSearchCriteria = new ChangeLogRecordSearchCriteria();
    	    changeLogSearchCriteria.setObjectId(activity.getId());
    	    changeLogSearchCriteria.setObjectTypeId(4);
    	    
    	    // Get change log records for spotlight activity
    	    ChangeLogRecordSet changeLogRecordSet = logService.getChangeLogRecords(changeLogSearchCriteria);
    	    for (ChangeLogRecord logRecord:changeLogRecordSet.getRecords()) {
    	    	if (("Add".equals(logRecord.getAction())) && ("Default Floodlight".equals(logRecord.getContext()))) {
    	    		floodlightTagRecords.put(extractFloodlightId(logRecord), logRecord);
    	    	}
    	    }
		
    	    // get Default Tags
			FloodlightTag[] floodlightTags = activity.getDefaultFloodlightTags();
			if (floodlightTags != null) {
				for (FloodlightTag floodlightTag : floodlightTags) {
					ChangeLogRecord record = floodlightTagRecords.get(floodlightTag.getId());
					out(
						activity.getId() + "\t" + // spotlight tag ID
						activity.getName() + "\t" + // spotlight name
						activity.isSecure() + "\t" + // secure status
						"\t" + // placeholder for vendor name
						floodlightTag.getId() + "\t" + // vendor pixel ID
						floodlightTag.getName() + "\t" + // vendor pixel description
						//((GregorianCalendar)record.getChangeDate()).getTime() + "\t" + // vendor pixel added date
						"\t" + // vendor pixel added date
						floodlightTag.getUrl().replaceAll("\\\n", "") + "\t\n", // vendor pixel code
						bw
					);
				}
			}
			
    	}
    	spotSearchCriteria.setPageNumber(spotSearchCriteria.getPageNumber()+1);
	} while (spotSearchCriteria.getPageNumber() <= spotRecordSet.getTotalNumberOfPages());
  }
  
  private static void getPublisherTags(DfaServices dfaServices, DfaSession session, long advertiserId) throws Exception {
	/*
	 * Get and print advertiser data
	 */
	AdvertiserRemote advService = dfaServices.get(session, AdvertiserRemote.class);
	AdvertiserSearchCriteria advSearchCriteria = setAdvertiserSearchCriteria(advertiserId);
	AdvertiserRecordSet advRecordSet = advService.getAdvertisers(advSearchCriteria);
	printAdvertiserInfo(advRecordSet);

	// Print out publisher tags headers
	out("Spotlight Tag ID\t", bw);
	out("Spotlight Tag Name\t", bw);
	out("Is Spotlight Secure?\t", bw);
	out("Vendor Pixel ID\t", bw);
	out("DFA Site\t", bw);
	out("DFA Site ID\t", bw);
	out("Click-Through Enabled?\t", bw);
	out("View-Through Enabled?\t", bw);
	out("Vendor Pixel Code\t", bw);
	out("\n", bw);

	/*
	 * Get spotlight data
	 */
	// Request the Spotlight service
	SpotlightRemote spotService = dfaServices.get(session, SpotlightRemote.class);

	// Create spotlight search criteria structure
	SpotlightActivitySearchCriteria spotSearchCriteria = setSpotlightActivitySearchCriteria(advertiserId);
	SpotlightActivityRecordSet spotRecordSet;

	// Get spotlight activities generated for this advertiser
	do {
		spotRecordSet = spotService.getSpotlightActivities(spotSearchCriteria);

		for (SpotlightActivity activity : spotRecordSet.getRecords()) {

		    // get Publisher Tags
			FloodlightPublisherTag[] floodlightPubTags = activity.getPublisherTags();
			if (floodlightPubTags != null) {
				for (FloodlightPublisherTag floodlightPubTag : floodlightPubTags) {
					  out(
							activity.getId() + "\t" + // spotlight tag ID
							activity.getName() + "\t" + // spotlight name
							activity.isSecure() + "\t" + // secure status
							floodlightPubTag.getId() + "\t" + // vendor pixel ID
							floodlightPubTag.getName() + "\t" + // DFA Site
							floodlightPubTag.getSiteId() + "\t" + // DFA Site ID
							floodlightPubTag.isClickThrough() + "\t" + // click-through enabled status
							floodlightPubTag.isViewThrough() + "\t" + // view-through enabled status
							floodlightPubTag.getUrl().replaceAll("\\\n", "") + "\t\n", // vendor pixel code
							bw
					  );
				 }
			}

		}
		spotSearchCriteria.setPageNumber(spotSearchCriteria.getPageNumber()+1);
	} while (spotSearchCriteria.getPageNumber() <= spotRecordSet.getTotalNumberOfPages());
  }
  
  // helper function to check that main method's parameters are input correctly
  private static boolean checkArgs(String[] args) {
	  // check for correct number of arguments
	  if (args.length != 5) {
		  System.out.println("You have not supplied all 5 required arguments - username, password, advertiser Id, tag type, file name.");
		  return false;
	  }
	  // check that strings are passed for username and password (parameters 1 and 2) 
	  if (!(args[0] instanceof String) || !(args[1] instanceof String)) {
		  System.out.println("Username (parameter 1) and password (parameter 2) must be supplied as strings.");
		  return false;
	  }
	  // check tag type argument passed for tag type (parameter 4)
	  int arg3 = Integer.parseInt(args[3]);
	  if (arg3 != 1 && arg3 != 2 && arg3 != 3 && arg3 != 4) {
		  System.out.println("Tag type must be supplied as 1, 2, 3, or 4 (Spotlight Tags, Default Tags, Publisher Tags, All Tag Types)");
		  return false;
	  }
	  // check that string is passed for file name (parameter 5)
	  if (!(args[4] instanceof String)) {
		  System.out.println("File name must be supplied as string.");
		  return false;
	  }
	  return true;
  }
  
  // helper function to set DFA Advertiser search criteria
  private static AdvertiserSearchCriteria setAdvertiserSearchCriteria(long advertiserId) {
	  AdvertiserSearchCriteria advSearchCriteria = new AdvertiserSearchCriteria();
	  long[] advIds = new long[1];
	  advIds[0] = advertiserId;
	  advSearchCriteria.setIds(advIds);
	  advSearchCriteria.setPageSize(1);
	  advSearchCriteria.setPageNumber(1);
	  return advSearchCriteria;
  }
  
  // helper function to set DFA Spotlight Activity search criteria
  private static SpotlightActivitySearchCriteria setSpotlightActivitySearchCriteria(long advertiserId) {
	  SpotlightActivitySearchCriteria spotSearchCriteria = new SpotlightActivitySearchCriteria();
	  spotSearchCriteria.setAdvertiserId(advertiserId);
	  spotSearchCriteria.setPageSize(100);
	  spotSearchCriteria.setPageNumber(1);
	  return spotSearchCriteria;
  }
  //overloaded, including filter for Archive status
  private static SpotlightActivitySearchCriteria setSpotlightActivitySearchCriteria(long advertiserId, boolean activeFilter) {
	  SpotlightActivitySearchCriteria spotSearchCriteria = new SpotlightActivitySearchCriteria();
	  spotSearchCriteria.setAdvertiserId(advertiserId);
	  spotSearchCriteria.setPageSize(100);
	  spotSearchCriteria.setPageNumber(1);
	  ActiveFilter af = new ActiveFilter();
	  if (activeFilter == true) {
		  af.setActiveOnly(true);
	  } else {
		  af.setInactiveOnly(true);
	  }
	  spotSearchCriteria.setActiveFilter(af);
	  return spotSearchCriteria;
  }
  
  // helper function to print DFA Advertiser information
  private static void printAdvertiserInfo(AdvertiserRecordSet advRecordSet) throws IOException {
	  for (Advertiser advertiser:advRecordSet.getRecords()) {
		  out("Advertiser ID:\t"+advertiser.getId()+"\n", bw);
		  out("Advertiser Name:\t"+advertiser.getName()+"\n", bw);
		  out("\n", bw);
	  }
  }
  
  // helper function to dual-stream text to CLI and text file
  private static void out(String text, BufferedWriter bw) throws IOException {
	  // write to file
	  bw.write(text);
	  
	  // print to CLI
	  System.out.print(text);
  }
  
  // helper function to create output files
  private static void createFile(String fileName) throws IOException {
	  file = new File(fileName);
	  if (!file.exists()) {
		  file.createNewFile();
	  }
	  fw = new FileWriter(file.getAbsoluteFile());
	  bw = new BufferedWriter(fw);
  }
  
  // helper function to extract default floodlight tag Id from change log record
  private static long extractFloodlightId(ChangeLogRecord record) {
//System.out.println("object id: "+record.getObjectId()+", "+record.getNewValue());
		String str = "0";
		Pattern p = Pattern.compile("(\\d{6,7})( - )");
		Matcher m = p.matcher(record.getNewValue());
		if (m.find()) {
		  str = m.group(1);
		}
//System.out.println("str: "+str);
		return Long.parseLong(str);
  }
   
}
