package infoRetrieval;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.analysis.core.SimpleAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.document.Field.Store;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class MainClass {
	private static List<Topic> topics;
	public static void main(String[] args) {
        
		//indexDirectory();
		//getTopics();
		//searchAllTopics();
		printResultsToTable();
		
    }   
	
	/*
	 * Function returns list of files in directory
	 */
	private static List<File> getFilesInDirectory(File dir) {
		File[] listOfFiles = dir.listFiles();
		List<File> res=new ArrayList<File>();
	    for (int i = 0; i < listOfFiles.length; i++) {
	      if (listOfFiles[i].isFile()) {
	        res.add(listOfFiles[i]);
	      } else if (listOfFiles[i].isDirectory()) {
	        res.addAll(getFilesInDirectory(listOfFiles[i]));
	      }
	    }
	    return res;
	}
	/*
	 * Procedure indexes all files in directory and its subdirectories
	 */
	 private static void indexDirectory() {          
         try {  
        	 //where the indexes will be saved
         Path path = Paths.get("indexes");
         Directory directory = FSDirectory.open(path);
         IndexWriterConfig config = new IndexWriterConfig(new SimpleAnalyzer());        
         IndexWriter indexWriter = new IndexWriter(directory, config);
         indexWriter.deleteAll();
         //where are the files to be indexed
         File f = new File("collection"); 
         List<File> allFiles=getFilesInDirectory(f);
             for (File file : allFiles) {
                    System.out.println("indexed " + file.getCanonicalPath()); 
                    Document doc = new Document();
                    FileInputStream is = new FileInputStream(file);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                    StringBuffer stringBuffer = new StringBuffer();
                    String line = null;
                    boolean headline=false;
                    boolean text=false;
                    String h="";
                    String t="";
                    String docno="";
                    while((line = reader.readLine())!=null){
                      stringBuffer.append(line).append("\n");
                      if (line.indexOf("<DOCNO>")!=-1) {
                    	  docno=line.replace("<DOCNO>", "");
                    	  docno=line.replace("</DOCNO>", "");
                    	  docno=line.replace(" ", "");
                    	  doc = new Document();
                          doc.add(new TextField("path", file.getName(), Store.YES));
                          doc.add(new TextField("docno",docno, Store.YES));
                      }
                      else if (line.indexOf("<HEADLINE>")!=-1){
                    	  headline=true;
                      }
                      else if (line.indexOf("<TEXT>")!=-1){
                    	  text=true;
                      }
                      else if (line.indexOf("</HEADLINE>")!=-1){
                    	  headline=false;
                      }
                      else if (line.indexOf("</TEXT>")!=-1){
                    	  text=false;
                      }
                      else if (headline) {
                    	 h+=line; 
                      }
                      else if (text){
                    	  t+=line;
                      } 
                      else if (line.indexOf("</DOC>")!=-1){
                    	  doc.add(new TextField("headline",h, Store.YES));
                 		  doc.add(new TextField("text",t, Store.YES));                             	  
                    	  indexWriter.addDocument(doc);
                    	  h="";
                    	  t="";
                      }
                    	  
                    }
                    reader.close(); 
                    	 }             
             indexWriter.close();           
             directory.close();
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }                   
    }
	/*
	 * Procedure reads topics from text file.
	 */
	 private static void getTopics(){
		 topics=new ArrayList<Topic>();
			BufferedReader br = null;
			try {

				String sCurrentLine;
				int id=0;
				String title="";
				String description="";
				String narrative="";
				boolean desc=true;
				br = new BufferedReader(new FileReader("topicsTREC8Adhoc.txt"));
				while ((sCurrentLine = br.readLine()) != null) {
					if (sCurrentLine.indexOf("<num>")!=-1) {
						id=Integer.parseInt(sCurrentLine.replaceAll("[^0-9]", ""));
					}
					else if (sCurrentLine.indexOf("<title>")!=-1) {
						title=sCurrentLine.replace("<title> ", "");
					}
					else if (sCurrentLine.indexOf("<desc>")!=-1) {
						desc=true;
					}
					else if (sCurrentLine.indexOf("<narr>")!=-1) {
						desc=false;
					}
					else if (sCurrentLine.indexOf("</top>")!=-1) {
						topics.add(new Topic(id,title,description,narrative));
						title="";
						description="";
						narrative="";
					}
					else if (sCurrentLine!="" && sCurrentLine.indexOf("<top>")==-1){
						if (desc) {
							description+=sCurrentLine+" ";
						}
						else {
							narrative+=sCurrentLine+" ";
						}
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (br != null)br.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
			
	 }
	 /*
	  * From each topic take the keywords and use search fc to query the results
	  */
	 private static void searchAllTopics(){
		 for (Topic t:topics) {
			 String query=t.getTitle().replace(",", " OR");
			 search(t,query); 
		 }
	 }

	/*
	 * Search for given query
	 */
    private static void search(Topic t,String text) { 
    	PrintWriter writer;
        try { 
        	writer =new PrintWriter(new FileWriter("bm25L-result.txt",true));
            Path path = Paths.get("indexes");
            Directory directory = FSDirectory.open(path);       
            IndexReader indexReader =  DirectoryReader.open(directory);
            IndexSearcher indexSearcher = new IndexSearcher(indexReader);
            indexSearcher.setSimilarity(new BM25L());
            MultiFieldQueryParser queryParser = new MultiFieldQueryParser(new String[] {"headline", "text"},new StandardAnalyzer());  
            Query query = queryParser.parse(text);
            TopDocs topDocs = indexSearcher.search(query,1000);
            //System.out.println("totalHits " + topDocs.totalHits);
           
            int ranking=1;
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {    
            	org.apache.lucene.document.Document document = indexSearcher.doc(scoreDoc.doc);
            	String docno=String.valueOf(document.getField("docno"));
            	docno=docno.substring(docno.indexOf(">")+1, docno.indexOf("</"));
            	String line=t.getId()+" Q0 "+docno+" "+ranking+" "+scoreDoc.score+" assignment1";
            	System.out.println(line);
            	
            	writer.println(line);
                ranking++;
                
            }
            writer.close();
        } catch (Exception e) {
            // TODO: handle exception
            e.printStackTrace();
        }               
    }
    
	 /*
	  * Read attached trec_eval output files and create the result file with the desired formatting
	  */
	private static void printResultsToTable(){
		HashMap<Integer,Float> Lucene=new HashMap<Integer,Float>();
		HashMap<Integer,Float> BM25=new HashMap<Integer,Float>();
		HashMap<Integer,Float> BM25L=new HashMap<Integer,Float>();
		String[] names={"out-lucene.txt","out-bm25.txt","out-bm25L.txt"};
		int iter=0;
		for (String s:names) {
			BufferedReader br = null;
			try {
				String sCurrentLine;
				br = new BufferedReader(new FileReader(s));
				while ((sCurrentLine = br.readLine()) != null) {
					if (sCurrentLine.indexOf("map")!=-1) {
						String[] line=sCurrentLine.split("\t");
						if (!line[1].equals("all")) {
							if (iter==0) Lucene.put(Integer.parseInt(line[1]), Float.parseFloat(line[2]));
							if (iter==1) BM25.put(Integer.parseInt(line[1]), Float.parseFloat(line[2]));
							if (iter==2) BM25L.put(Integer.parseInt(line[1]), Float.parseFloat(line[2]));
						} else {
							if (iter==0) Lucene.put(1000, Float.parseFloat(line[2]));
							if (iter==1) BM25.put(1000, Float.parseFloat(line[2]));
							if (iter==2) BM25L.put(1000, Float.parseFloat(line[2]));
						}
						
					}
				}

			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				try {
					if (br != null)br.close();
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
			iter++;
		}
		
		//print it to out file
		PrintWriter writer;
       try { 
       	writer =new PrintWriter(new FileWriter("table-result.txt",true));
       	 writer.println("TopicNb \t Lucene \t BM25 \t BM25L");
          for (int topic:Lucene.keySet()) {
       	   if (topic!=1000) {
       		   writer.println(topic+"\t"+Lucene.get(topic)+"\t"+BM25.get(topic)+"\t"+BM25L.get(topic)); 
       	   }
       	   else {
       		   writer.println("AVG \t"+Lucene.get(topic)+"\t"+BM25.get(topic)+"\t"+BM25L.get(topic));  
       	   }        	   
          } 
           writer.close();
       } catch (Exception e) {
           // TODO: handle exception
           e.printStackTrace();
       }          
	}
}
