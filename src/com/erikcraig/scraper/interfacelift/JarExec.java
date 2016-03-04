package com.erikcraig.scraper.interfacelift;
import static org.kohsuke.args4j.ExampleMode.ALL;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;

class ImageObjectID {
	private String id;
	private String title;
	private String matchedURL;
	
	ImageObjectID(String id, String title, String matchedUrl){
		this.id = id;
		this.title = title;
		this.matchedURL = matchedUrl;
	}
	
	public String getMatchedURL() {
		return matchedURL;
	}

	public ImageObjectID setMatchedURL(String matchedURL) {
		this.matchedURL = matchedURL;
		return this;
	}

	public String getId() {
		return id;
	}
	public ImageObjectID setId(String id) {
		this.id = id;
		return this;
	}
	public String getTitle() {
		return title;
	}
	public ImageObjectID setTitle(String title) {
		this.title = title;
		return this;
	}
	
	
}

public class JarExec {
	
	private final static String HTMLPGBASEURL = "https://interfacelift.com/wallpaper/downloads/date/any/";
	private final static String JSURL = "http://interfacelift.com/inc_NEW/jscript002.js";
	private final static String previewUrlExtractionPattern = "interfacelift\\.com/wallpaper/previews/([0-9]{5})_(.{1,})_[0-9]{3,4}x[0-9]{3,4}\\.jpg";
	private final static String jsUrlExtractionPattern = "document.getElementById\\('download_'\\+id\\).innerHTML = \"<a href=\\\\\"\\/wallpaper\\/(.{1,})\\/\"\\+";
	private static final String USER_AGENT = "Mozilla/5.0 (iPhone; U; CPU iPhone OS 4_3_3 like Mac OS X; en-us) AppleWebKit/533.17.9 (KHTML, like Gecko) Version/5.0.2 Mobile/8J2 Safari/6533.18.5";
	private static final String USAGE_STR = "java interfaceLiftScraper [options...] pathToWriteTo";
	
    @Option(name="-o",usage="Full file system path to save images to",metaVar="PATH")
    private String FILE_PATH = System.getProperty("user.dir");
    
    @Option(name="-res",usage="Image resolution to download",metaVar="RES")
    private String RESOLUTION = "3840x2160";
    
    @Option(name="-f",usage="Perform full update. Does not quit as soon as the first already existing image is found.")
    private boolean fullUpdate;

    @Option(name="-n",usage="Number of recent images to download.")
    private int NUMBER_TO_DOWNLOAD = 100;
    
    @Option(name="-h",usage="Display usage.",help=true)
    private boolean isHelp;
    
    @Argument
    private List<String> arguments = Lists.newArrayList();
    
	public static void main(String[] args) {
		new JarExec().doMain(args);
	}

	private void doMain(String[] args) {
		try {
			
			CmdLineParser parser = new CmdLineParser(this);
	        
	        try {
	            // parse the arguments.
	            parser.parseArgument(args);

	            String allArgs = Arrays.toString(args);
	            if (isHelp){
	            	System.out.println(USAGE_STR);
	            	parser.printUsage(System.out);
	            	System.out.println("Example: java interfaceLiftScraper"+parser.printExample(ALL));
	            	return;
	            }
	            

	        } catch( CmdLineException e ) {
	            // if there's a problem in the command line,
	            // you'll get this exception. this will report
	            // an error message.
	            System.err.println(e.getMessage());
	            System.err.println(USAGE_STR);
	            // print the list of available options
	            parser.printUsage(System.err);
	            System.err.println();

	            // print option sample. This is useful some time
	            System.err.println("  Example: java SampleMain"+parser.printExample(ALL));

	            return;
	        }

	        if (!FILE_PATH.endsWith("/")){
	        	FILE_PATH+="/";
	        }
	        
			int numberOfPages = NUMBER_TO_DOWNLOAD / 10; //10 per-page today
			int totalSaved = 0;
			int i = 0;
			while (i < (numberOfPages + 1)){
				int saved = doScrape(i);
				if (saved == -1){
					break;
				}
				else{
					totalSaved+=saved;
					i++;
				}
			}
			System.out.println("Downloaded images: "+totalSaved);
			System.out.println("Skipped images: "+(NUMBER_TO_DOWNLOAD - totalSaved));
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	int doScrape(int index) throws UnsupportedOperationException, IOException{
		String theHtml = null;
		String theJs = null;
		int saved = 0;
		
		CloseableHttpClient httpclient = HttpClients.createDefault();
		HttpGet httpget = new HttpGet(HTMLPGBASEURL+"index"+index+".html");
		httpget.setHeader(HttpHeaders.USER_AGENT, USER_AGENT);
		httpget.setHeader(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
		CloseableHttpResponse response = httpclient.execute(httpget);
		try {
		    HttpEntity entity = response.getEntity();
		    if (entity != null) {
		        InputStream instream = entity.getContent();
		        StringWriter writer = new StringWriter();
		        IOUtils.copy(instream, writer);
		        theHtml = writer.toString();
		    }
		} finally {
		    response.close();
		}
		
		//Drop out if HTML does not read
		if (Strings.isNullOrEmpty(theHtml)){
			System.out.println("Couldn't read index.html for "+HTMLPGBASEURL);
			return saved;
		}
		
		
		
		Pattern p = Pattern.compile(previewUrlExtractionPattern);
	    Matcher m = p.matcher(theHtml); // get a matcher object
	    
	    List<ImageObjectID> foundImages = Lists.newArrayList();
	    
	    while(m.find()) {
		    foundImages.add(new ImageObjectID(m.group(1), m.group(2), m.group(0)));
	    }

		
		httpget = new HttpGet(JSURL);
		response = httpclient.execute(httpget);
		try {
		    HttpEntity entity = response.getEntity();
		    if (entity != null) {
		        InputStream instream = entity.getContent();
		        StringWriter writer = new StringWriter();
		        IOUtils.copy(instream, writer);
		        theJs = writer.toString();
//		        System.out.println(theJs);
		    }
		} finally {
		    response.close();
		}
		//Drop out if HTML does not read
		if (Strings.isNullOrEmpty(theJs)){
			System.out.println("Couldn't read index.html for "+JSURL);
			return saved;
		}
		
		p = Pattern.compile(jsUrlExtractionPattern);
	    m = p.matcher(theJs); // get a matcher object
	    String pathId = null;
	    
	    while(m.find()) {
	    	pathId = m.group(1);
	    }
	    httpclient.close();
	    
		if (Strings.isNullOrEmpty(pathId)){
			System.out.println("path ID could not be found!");
			return saved;
		}else{
			//do full retrieval here
			
			for (ImageObjectID thisEntry : foundImages){
				
				
				String thisFilePath = this.FILE_PATH+thisEntry.getTitle()+"_"+RESOLUTION+".jpg";
				
		        File f = new File(thisFilePath);
		        if(f.exists()) { 
		        	System.out.println("Already created "+thisFilePath+" - skipping");
		        	//if running in fullUpdate mode - continue looking for missing jpgs, otherwise quit the update.
		        	if (fullUpdate)
		        		continue;
		        	else
		        		return -1;
		        }
				
				
				String thisUrl = "http://interfacelift.com/wallpaper/"+pathId+"/"+thisEntry.getId()+"_"+thisEntry.getTitle()+"_"+RESOLUTION+".jpg";
				System.out.println("Getting "+thisUrl);
				httpclient = HttpClients.createDefault();
				HttpGet get = new HttpGet(thisUrl);
				long start = System.currentTimeMillis();
				response = httpclient.execute(get);
		        BufferedReader rd = new BufferedReader(new InputStreamReader(
		                response.getEntity().getContent()));
		        
				InputStream data = response.getEntity().getContent();
				try {
				    OutputStream output = new FileOutputStream(thisFilePath);
				    try {
				        ByteStreams.copy(data, output);
				        long stop = System.currentTimeMillis();
				        System.out.println("Time to retrieve image at "+thisUrl+": "+(stop-start)+"ms");
				    } finally {
				        output.close();
				    }
				    System.out.println("Wrote file to: "+thisFilePath);
				    saved++;
				} finally {
				}
				response.close();
				httpclient.close();
			}
			
			
		}
		return saved;
		
	}
	
	
}
