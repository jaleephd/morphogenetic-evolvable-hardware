//			 ContentionLookup
//
// this class implements a lookup for contentious JBits resources
// from a table that is loaded from a file containing the necessary
// contention information.
//
// The contention information file is in the format of:
// contentious-virtex-bits-const "," contentious-virtex-value-const <newline>
// <tab> competing-virtex-bits-const "," competing-virtex-value-const \
//	"," competing CLB row offset "," competing CLB col offset <newline>
// <tab> another competing-virtex-bits-const ...
//
// note that the contentious virtex bits constant must start at the start of
// the line, and all lines that follow with a leading tab define competing
// virtex resources. Also, the offsets are integers, typically -1,0,+1 for
// single lines.
//
// this class also requires local classes: ContentionEntry, StringSplitter
//
// 		written December 2003, J.A. Lee


// for String's etc
import java.lang.*;
// for collection classes (eg Map)
import java.util.*;
// for IO
import java.io.*;


class ContentionLookup {

  Map contentionTable;


public static void main (String[] args)
{
  ContentionLookup cl=null;

  if (args.length<1) {
    System.err.println("need to specify contention information file!");
    System.exit(1);
  }

  cl=new ContentionLookup(args[0]);
  System.out.println("loaded table...");
  System.out.println("printing contents...");
  cl.printTable();
  System.out.println("finished...");

}


 
// use null or "" if no table to load
public ContentionLookup(String contentionInfoFile)
{
  contentionTable=new HashMap();
  if (contentionInfoFile!=null && contentionInfoFile.length()>0) {
    loadTableFromFile(contentionInfoFile);
  }
}


public List getList(String bits, String value)
{
  return (List)contentionTable.get(bits+","+value);
}


// print the state of the table for debugging purposes
public void printTable()
{
  System.out.println("contentionTable: "+contentionTable);
}


void loadTableFromFile(String contentionInfoFile)
{
  InputStream inputstream=null;
  InputStreamReader inputstreamreader=null;
  BufferedReader bufferedreader=null;

  int linenum=0;
  String line=null;
  String[] fields;
  boolean nextListEntry=false;
  ContentionEntry entry=null;
  String resource=null;

  try {
    // open the file and create a buffered input stream reader
    inputstream = new FileInputStream(contentionInfoFile);
    inputstreamreader=new InputStreamReader(inputstream);
    bufferedreader = new BufferedReader(inputstreamreader);
  } catch (Exception e) {
    System.err.println("Error opening " + contentionInfoFile + "\n" + e);
    System.exit(1);
  }

  // read the file
  try {
    while ((line = bufferedreader.readLine()) != null) {
      linenum++;
      // each competing entry in the list is indicated with a leading tab
      nextListEntry=(line.length()>0 && line.charAt(0)=='\t');
      line=line.trim();		// strip leading & trailing whitespace
//System.out.println(line);
      // ignore empty lines, and lines starting with a '#' indicating a comment
      if (line.length()>0 && line.charAt(0)!='#') {
        // get JBits bits and value constant names and trim whitespace
        fields=StringSplitter.trimsplit(line,",");
        if (fields.length>=2) {
//System.out.println("got "+fields.length+" fields");
	  if (!nextListEntry) {
//System.out.println("got new resource: "+resource);
            // new resource for which to build up a contention list
	    resource=getResource(fields,linenum);
	  } else {
//System.out.println("got next competing entry");
	    // add another entry for this resource's contention list
	    updateTable(resource,fields,linenum);
	  }
        } else {
          System.err.println("wrong number of fields on line "+linenum+"!");
	  System.exit(1);
        }
      }
    }
  } catch (Exception e) {
    System.err.println("Error reading file!");
    System.err.println(e);
    e.printStackTrace();
    System.err.println("exiting...");
    System.exit(1);
  }


  try { 
    // finished with file, so close it
    inputstream.close();
  } catch (Exception e) {
    System.err.println("Error closing file! exiting...");
    System.exit(1);
  }
 
}



String getResource(String[] fields, int linenum)
{
  if (fields.length>2) {
    System.err.println("too many fields for specifying new resource on "+linenum+"!");
    System.exit(1);
  }

  String resource=fields[0]+","+fields[1];
  return resource;
}



void updateTable(String resource, String[] fields, int linenum)
{
  if (resource==null) {
    System.err.println("competing entry without contentious resource specified on "+linenum+"!");
    System.exit(1);
  }
  if (fields.length<4) {
    System.err.println("competing entry requires 4 fields: "+fields.length+" specified on "+linenum+"!");
    System.exit(1);
  }

  // remove resource's list of entries to update it
  List clist=(List)contentionTable.remove(resource);

  // if no entries yet, then create a list
  if (clist==null) {
    clist=new ArrayList();
  }

  // strip leading & trailing spaces, and leading '+' signs
  String r=fields[2].trim();
  if (r.length()>0 && r.charAt(0)=='+') { r=r.substring(1); }
  String c=fields[3].trim();
  if (c.length()>0 && c.charAt(0)=='+') { c=c.substring(1); }

  // create new contention entry
  int row=Integer.parseInt(r);
  int col=Integer.parseInt(c);
  ContentionEntry entry=new ContentionEntry(fields[0],fields[1],row,col);

  // add entry to list
  clist.add(entry);

  // put updated list back in table
  contentionTable.put(resource,clist);
}





}// end class





