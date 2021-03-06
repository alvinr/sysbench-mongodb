//import com.mongodb.Mongo;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientOptions;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import com.mongodb.ServerAddress;
import com.mongodb.WriteConcern;
import com.mongodb.CommandResult;
import com.mongodb.AggregationOutput;
import com.mongodb.WriteResult;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Date;
import java.util.Properties;
import java.util.List;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.File;
import java.io.Writer;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Random;

public class jmongosysbenchexecute {
    public static AtomicLong globalInserts = new AtomicLong(0);
    public static AtomicLong globalDeletes = new AtomicLong(0);
    public static AtomicLong globalUpdates = new AtomicLong(0);
    public static AtomicLong globalPointQueries = new AtomicLong(0);
    public static AtomicLong globalRangeQueries = new AtomicLong(0);
    public static AtomicLong globalSysbenchTransactions = new AtomicLong(0);
    public static AtomicLong globalWriterThreads = new AtomicLong(0);
    
    public static Writer writer = null;
    public static boolean outputHeader = true;

    public static int numCollections;
    public static String dbName;
    public static int writerThreads;
    public static Integer numMaxInserts;
    public static long secondsPerFeedback;
    public static String logFileName;
    public static String indexTechnology;
    public static String readOnly;
    public static int runSeconds;
    public static String myWriteConcern;
    public static Integer maxTPS;
    public static Integer maxThreadTPS;
    public static String serverName;
    public static int serverPort;
    public static String collectionPerDB;
    
    public static long oltpRangeSize;
    public static long oltpPointSelects;
    public static long oltpSimpleRanges;
    public static long oltpSumRanges;
    public static long oltpOrderRanges;
    public static long oltpDistinctRanges;
    public static long oltpIndexUpdates;
    public static long oltpNonIndexUpdates;
    
    public static String cpuProfiler;

    public static boolean bIsTokuMX = false;
    
    public static int allDone = 0;
    
    public jmongosysbenchexecute() {
    }

    public static void main (String[] args) throws Exception {
        if (args.length != 22) {
            logMe("*** ERROR : CONFIGURATION ISSUE ***");
            logMe("jsysbenchexecute [number of collections] [database name] [number of writer threads] [documents per collection] [seconds feedback] "+
                                   "[log file name] [read only Y/N] [runtime (seconds)] [range size] [point selects] "+
                                   "[simple ranges] [sum ranges] [order ranges] [distinct ranges] [index updates] [non index updates] [writeconcern] [max tps] [server] [port] [cpu profiler]");
            System.exit(1);
        }
        
        numCollections = Integer.valueOf(args[0]);
        dbName = args[1];
        writerThreads = Integer.valueOf(args[2]);
        numMaxInserts = Integer.valueOf(args[3]);
        secondsPerFeedback = Long.valueOf(args[4]);
        logFileName = args[5];
        readOnly = args[6];
        runSeconds = Integer.valueOf(args[7]);
        oltpRangeSize = Integer.valueOf(args[8]);
        oltpPointSelects = Integer.valueOf(args[9]);
        oltpSimpleRanges = Integer.valueOf(args[10]);
        oltpSumRanges = Integer.valueOf(args[11]);
        oltpOrderRanges = Integer.valueOf(args[12]);
        oltpDistinctRanges = Integer.valueOf(args[13]);
        oltpIndexUpdates = Integer.valueOf(args[14]);
        oltpNonIndexUpdates = Integer.valueOf(args[15]);
        myWriteConcern = args[16];
        maxTPS = Integer.valueOf(args[17]);
        serverName = args[18];
        serverPort = Integer.valueOf(args[19]);
        collectionPerDB = args[20];
        cpuProfiler = args[21];


        maxThreadTPS = (maxTPS / writerThreads) + 1;
        
        WriteConcern myWC = new WriteConcern();
        if (myWriteConcern.toLowerCase().equals("fsync_safe")) {
            myWC = WriteConcern.FSYNC_SAFE;
        }
        else if ((myWriteConcern.toLowerCase().equals("none"))) {
            myWC = WriteConcern.NONE;
        }
        else if ((myWriteConcern.toLowerCase().equals("normal"))) {
            myWC = WriteConcern.NORMAL;
        }
        else if ((myWriteConcern.toLowerCase().equals("replicas_safe"))) {
            myWC = WriteConcern.REPLICAS_SAFE;
        }
        else if ((myWriteConcern.toLowerCase().equals("safe"))) {
            myWC = WriteConcern.SAFE;
        } 
        else {
            logMe("*** ERROR : WRITE CONCERN ISSUE ***");
            logMe("  write concern %s is not supported",myWriteConcern);
            System.exit(1);
        }
    
        logMe("Application Parameters");
        logMe("-------------------------------------------------------------------------------------------------");
        logMe("  collections              = %d",numCollections);
        logMe("  database name            = %s",dbName);
        logMe("  writer threads           = %d",writerThreads);
        logMe("  documents per collection = %,d",numMaxInserts);
        logMe("  feedback seconds         = %,d",secondsPerFeedback);
        logMe("  log file                 = %s",logFileName);
        logMe("  read only                = %s",readOnly);
        logMe("  run seconds              = %d",runSeconds);
        logMe("  oltp range size          = %d",oltpRangeSize);
        logMe("  oltp point selects       = %d",oltpPointSelects);
        logMe("  oltp simple ranges       = %d",oltpSimpleRanges);
        logMe("  oltp sum ranges          = %d",oltpSumRanges);
        logMe("  oltp order ranges        = %d",oltpOrderRanges);
        logMe("  oltp distinct ranges     = %d",oltpDistinctRanges);
        logMe("  oltp index updates       = %d",oltpIndexUpdates);
        logMe("  oltp non index updates   = %d",oltpNonIndexUpdates);
        logMe("  write concern            = %s",myWriteConcern);
        logMe("  maximum tps (global)     = %d",maxTPS);
        logMe("  maximum tps (per thread) = %d",maxThreadTPS);
        logMe("  Server:Port = %s:%d",serverName,serverPort);
        logMe("  collection per DB = %s",collectionPerDB);

        MongoClientOptions clientOptions = new MongoClientOptions.Builder().connectionsPerHost(2048).socketTimeout(60000).writeConcern(myWC).build();
        ServerAddress srvrAdd = new ServerAddress(serverName,serverPort);
        MongoClient m = new MongoClient(srvrAdd, clientOptions);

        logMe("mongoOptions | " + m.getMongoOptions().toString());
        logMe("mongoWriteConcern | " + m.getWriteConcern().toString());

        DB db = m.getDB(dbName);

        // determine server type : mongo or tokumx
        DBObject checkServerCmd = new BasicDBObject();
        CommandResult commandResult = db.command("buildInfo");

        // check if tokumxVersion exists, otherwise assume mongo
        if (commandResult.toString().contains("tokumxVersion")) {
            indexTechnology = "tokumx";
        }
        else
        {
            indexTechnology = "mongo";
        }

        logMe("  index technology         = %s",indexTechnology);
        logMe("-------------------------------------------------------------------------------------------------");

        try {
            writer = new BufferedWriter(new FileWriter(new File(logFileName)));
        } catch (IOException e) {
            e.printStackTrace();
        }

        if ((!indexTechnology.toLowerCase().equals("tokumx")) && (!indexTechnology.toLowerCase().equals("mongo"))) {
            // unknown index technology, abort
            logMe(" *** Unknown Indexing Technology %s, shutting down",indexTechnology);
            System.exit(1);
        }
        
        if (indexTechnology.toLowerCase().equals("tokumx")) {
            bIsTokuMX = true;
        }

        jmongosysbenchexecute t = new jmongosysbenchexecute();

        Thread[] tWriterThreads = new Thread[writerThreads];
        
        for (int i=0; i<writerThreads; i++) {
            tWriterThreads[i] = new Thread(t.new MyWriter(writerThreads, i, numMaxInserts, db, numCollections));
            tWriterThreads[i].start();
        }
        
        Thread reporterThread = new Thread(t.new MyReporter());
        reporterThread.start();
        reporterThread.join();
        
        if ( cpuProfiler.toLowerCase().equals("y") ) {
        	Thread profilerThread = new Thread(t.new MyProfiler(db));
        	profilerThread.start();
        }

        // wait for writer threads to terminate
        for (int i=0; i<writerThreads; i++) {
            if (tWriterThreads[i].isAlive())
                tWriterThreads[i].join();
        }
        
        try {
            if (writer != null) {
                writer.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        m.close();
        
        logMe("Done!");
    }
    
    class MyWriter implements Runnable {
        int threadCount; 
        int threadNumber; 
        int numTables;
        long numMaxInserts;
        int numCollections;
        DB db;
        
        long numInserts = 0;
        long numDeletes = 0;
        long numUpdates = 0;
        long numPointQueries = 0;
        long numRangeQueries = 0;
        
        Random rand;
        
        MyWriter(int threadCount, int threadNumber, int numMaxInserts, DB db, int numCollections) {
            this.threadCount = threadCount;
            this.threadNumber = threadNumber;
            this.numMaxInserts = numMaxInserts;
            this.db = db;
            this.numCollections = numCollections;
            rand = new java.util.Random((long) threadNumber);
        }
        
        // http://stackoverflow.com/questions/2546078/java-random-long-number-in-0-x-n-range
        private long nextLong(Random rng, long n) {
    	   long bits, val;
    	   do {
    	      bits = (rng.nextLong() << 1) >>> 1;
    	      val = bits % n;
    	   } while (bits-val+(n-1) < 0L);
    	   return val;
    	}
        
        public void run() {
            logMe("Writer thread %d : started",threadNumber);
            globalWriterThreads.incrementAndGet();

            long numTransactions = 0;
            long numLastTransactions = 0;
            long nextMs = System.currentTimeMillis() + 1000;
            
            String collectionName = "sbtest" + Integer.toString(rand.nextInt(numCollections)+1);
            if (collectionPerDB.toLowerCase().equals("y")) {
            	db = db.getSisterDB(collectionName);
            }
            
            DBCollection coll = db.getCollection(collectionName);
            
            while (allDone == 0) {
                if ((numTransactions - numLastTransactions) >= maxThreadTPS) {
                    // pause until a second has passed
                    while (System.currentTimeMillis() < nextMs) {
                        try {
                            Thread.sleep(20);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    numLastTransactions = numTransactions;
                    nextMs = System.currentTimeMillis() + 1000;
                }

                // if TokuMX, lock onto current connection (do not pool)
                if (bIsTokuMX) {
                    db.requestStart();
                    db.command("beginTransaction");
                }
                
                try {
                    if (bIsTokuMX) {
                        // make sure a connection is available, given that we are not pooling
                        db.requestEnsureConnection();
                    }
                    
                    BasicDBObject query = new BasicDBObject("_id", 0);
                    BasicDBObject columns = new BasicDBObject("c", 1).append("_id", 0);
                    
                    for (int i=1; i <= oltpPointSelects; i++) {
                        //for i=1, oltp_point_selects do
                        //   rs = db_query("SELECT c FROM ".. table_name .." WHERE id=" .. sb_rand(1, oltp_table_size))
                        //end
                        
                        // db.sbtest8.find({_id: 554312}, {c: 1, _id: 0})
                        
                        long startId = nextLong(rand, numMaxInserts)+1;
                        
                        query.put("_id", startId);
    
                        //BasicDBObject query = new BasicDBObject("_id", startId);
                        //BasicDBObject columns = new BasicDBObject("c", 1).append("_id", 0);
                        
                        try {
	                        DBObject myDoc = coll.findOne(query, columns);
	                        //System.out.println(myDoc);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        
                        globalPointQueries.incrementAndGet();
                    }
                    
                    query = new BasicDBObject("_id", 0);
                    columns = new BasicDBObject("c", 1).append("_id", 0);
                    
                    for (int i=1; i <= oltpSimpleRanges; i++) {
                        //for i=1, oltp_simple_ranges do
                        //   range_start = sb_rand(1, oltp_table_size)
                        //   rs = db_query("SELECT c FROM ".. table_name .." WHERE id BETWEEN " .. range_start .. " AND " .. range_start .. "+" .. oltp_range_size - 1)
                        //end
                       
                        //db.sbtest8.find({_id: {$gte: 5523412, $lte: 5523512}}, {c: 1, _id: 0})
                       
                        long startId = nextLong(rand, numMaxInserts)+1;
                        long endId = startId + oltpRangeSize - 1;
                       
                        //BasicDBObject query = new BasicDBObject("_id", new BasicDBObject("$gte", startId).append("$lte", endId));
                        //BasicDBObject columns = new BasicDBObject("c", 1).append("_id", 0);
                        query.put("_id", new BasicDBObject("$gte", startId).append("$lte", endId));

                        DBCursor cursor = coll.find(query, columns);
                        try {
                            while(cursor.hasNext()) {
                                cursor.next();
                                //System.out.println(cursor.next());
                            }
                        } 
                        catch (Exception e) {
                            e.printStackTrace();
                        } 
                        finally {
                            cursor.close();
                        }
                        
                        globalRangeQueries.incrementAndGet();
                    }
    
                    // build the $projection operation
                    DBObject fields = new BasicDBObject("k", 1);
                    fields.put("_id", 0);
                    DBObject project = new BasicDBObject("$project", fields );

                    // Now the $group operation
                    DBObject groupFields = new BasicDBObject( "_id", null);
                    groupFields.put("average", new BasicDBObject( "$sum", "$k"));
                    DBObject group = new BasicDBObject("$group", groupFields);

                    // create our pipeline operations, first with the $match
                    DBObject match = new BasicDBObject("$match", 0);

                    for (int i=1; i <= oltpSumRanges; i++) {
                        //for i=1, oltp_sum_ranges do
                        //   range_start = sb_rand(1, oltp_table_size)
                        //   rs = db_query("SELECT SUM(K) FROM ".. table_name .." WHERE id BETWEEN " .. range_start .. " AND " .. range_start .. "+" .. oltp_range_size - 1)
                        //end
                       
                        //db.sbtest8.aggregate([ {$match: {_id: {$gt: 5523412, $lt: 5523512}}}, { $group: { _id: null, total: { $sum: "$k"}} } ])   
    
                        long startId = nextLong(rand, numMaxInserts)+1;
                        long endId = startId + oltpRangeSize - 1;
    
                        // create our pipeline operations, first with the $match
                        //DBObject match = new BasicDBObject("$match", new BasicDBObject("_id", new BasicDBObject("$gte", startId).append("$lte", endId)));
                        match.put("$match", new BasicDBObject("_id", new BasicDBObject("$gte", startId).append("$lte", endId)));                        
                        
                        // run aggregation
                        try {
                        	AggregationOutput output = coll.aggregate( match, project, group );
                            //System.out.println(output.getCommandResult());
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        } 
    
                        globalRangeQueries.incrementAndGet();
                    }
                    
                    query = new BasicDBObject("_id", 0);
                    columns = new BasicDBObject("c", 1).append("_id", 0);
                   
                    for (int i=1; i <= oltpOrderRanges; i++) {
                        //for i=1, oltp_order_ranges do
                        //   range_start = sb_rand(1, oltp_table_size)
                        //   rs = db_query("SELECT c FROM ".. table_name .." WHERE id BETWEEN " .. range_start .. " AND " .. range_start .. "+" .. oltp_range_size - 1 .. " ORDER BY c")
                        //end
                    
                        //db.sbtest8.find({_id: {$gte: 5523412, $lte: 5523512}}, {c: 1, _id: 0}).sort({c: 1})
                        
                        long startId = nextLong(rand, numMaxInserts)+1;
                        long endId = startId + oltpRangeSize - 1;
                       
                        //BasicDBObject query = new BasicDBObject("_id", new BasicDBObject("$gte", startId).append("$lte", endId));
                        //BasicDBObject columns = new BasicDBObject("c", 1).append("_id", 0);
                        query.put("_id", new BasicDBObject("$gte", startId).append("$lte", endId));
                        
                        DBCursor cursor = coll.find(query, columns).sort(new BasicDBObject("c",1));
                        try {
                            while(cursor.hasNext()) {
                                cursor.next();
                                //System.out.println(cursor.next());
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        } 
                        finally {
                            cursor.close();
                        }
                        
                        globalRangeQueries.incrementAndGet();
                    }

                    query = new BasicDBObject("_id", 0);
                    columns = new BasicDBObject("c", 1).append("_id", 0);
                    
                    for (int i=1; i <= oltpDistinctRanges; i++) {
                        //for i=1, oltp_distinct_ranges do
                        //   range_start = sb_rand(1, oltp_table_size)
                        //   rs = db_query("SELECT DISTINCT c FROM ".. table_name .." WHERE id BETWEEN " .. range_start .. " AND " .. range_start .. "+" .. oltp_range_size - 1 .. " ORDER BY c")
                        //end
                       
                        //db.sbtest8.distinct("c",{_id: {$gt: 5523412, $lt: 5523512}}).sort()
                        
                        long startId = nextLong(rand, numMaxInserts)+1;
                        long endId = startId + oltpRangeSize - 1;
                       
                        //BasicDBObject query = new BasicDBObject("_id", new BasicDBObject("$gte", startId).append("$lte", endId));
                        //BasicDBObject columns = new BasicDBObject("c", 1).append("_id", 0);
                        query.put("_id", new BasicDBObject("$gte", startId).append("$lte", endId));
                        
                        try {
	                        List lstDistinct = coll.distinct("c", query);
	                        //System.out.println(lstDistinct.toString());
                        }
                        catch (Exception e) {
	                            e.printStackTrace();
	                    } 
                        
                        globalRangeQueries.incrementAndGet();
                    }
                    
                
                    if (readOnly.toLowerCase().equals("n")) {
                        for (int i=1; i <= oltpIndexUpdates; i++) {
                            //for i=1, oltp_index_updates do
                            //   rs = db_query("UPDATE " .. table_name .. " SET k=k+1 WHERE id=" .. sb_rand(1, oltp_table_size))
                            //end
    
                            //db.sbtest8.update({_id: 5523412}, {$inc: {k: 1}}, false, false)
                            
                            long startId = nextLong(rand, numMaxInserts)+1;
                            
                            try {
                            	WriteResult wrUpdate = coll.update(new BasicDBObject("_id", startId), new BasicDBObject("$inc", new BasicDBObject("k",1)), false, false);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            } 

    
                            //System.out.println(wrUpdate.toString());
                        }
    
                        for (int i=1; i <= oltpNonIndexUpdates; i++) {
                            //for i=1, oltp_non_index_updates do
                            //   c_val = sb_rand_str("###########-###########-###########-###########-###########-###########-###########-###########-###########-###########")
                            //   query = "UPDATE " .. table_name .. " SET c='" .. c_val .. "' WHERE id=" .. sb_rand(1, oltp_table_size)
                            //   rs = db_query(query)
                            //   if rs then
                            //     print(query)
                            //   end
                            //end
    
                            //db.sbtest8.update({_id: 5523412}, {$set: {c: "hello there"}}, false, false)
                            
                            long startId = nextLong(rand, numMaxInserts)+1;
    
                            String cVal = sysbenchString(rand, "###########-###########-###########-###########-###########-###########-###########-###########-###########-###########");
    
                            try {
                            	WriteResult wrUpdate = coll.update(new BasicDBObject("_id", startId), new BasicDBObject("$set", new BasicDBObject("c",cVal)), false, false);
                            }
                            catch (Exception e) {
                                e.printStackTrace();
                            } 

                            
                            //System.out.println(wrUpdate.toString());
                        }
                        
                        
                        //i = sb_rand(1, oltp_table_size)
                        //rs = db_query("DELETE FROM " .. table_name .. " WHERE id=" .. i)
                      
                        //db.sbtest8.remove({_id: 5523412})
                        
                        long startId = nextLong(rand, numMaxInserts)+1;
                        //int nextRandom = rand.nextInt(numMaxInserts-threadCount)+1;
                        //int startId = threadNumber + (threadCount * (nextRandom / threadCount));
                        WriteResult wrRemove = null;
                        try {
                        	wrRemove = coll.remove(new BasicDBObject("_id", startId));
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        } 

    
    
                        //c_val = sb_rand_str([[###########-###########-###########-###########-###########-###########-###########-###########-###########-###########]])
                        //pad_val = sb_rand_str([[###########-###########-###########-###########-###########]])
                        //rs = db_query("INSERT INTO " .. table_name ..  " (id, k, c, pad) VALUES " .. string.format("(%d, %d, '%s', '%s')",i, sb_rand(1, oltp_table_size) , c_val, pad_val))
                
                        if ( wrRemove.getN() == 1 ) {
	                        BasicDBObject doc = new BasicDBObject();
	                        doc.put("_id",startId);
	                        doc.put("k",nextLong(rand, numMaxInserts)+1);
	                        String cVal = sysbenchString(rand, "###########-###########-###########-###########-###########-###########-###########-###########-###########-###########");
	                        doc.put("c",cVal);
	                        String padVal = sysbenchString(rand, "###########-###########-###########-###########-###########");
	                        doc.put("pad",padVal);
	                        try {
	                        	WriteResult wrInsert = coll.insert(doc);
	                        }
	                        catch (Exception e) {
	                            e.printStackTrace();
	                        } 
                        }
                        else {
                        	logMe("_id %d already removed in %s",startId, collectionName);
                        }
                    }
                
                    globalSysbenchTransactions.incrementAndGet();
                    numTransactions += 1;
               
                } finally {
                    if (bIsTokuMX) {
                        // commit the transaction and release current connection in the pool
                        db.command("commitTransaction");
                        //--db.command("rollbackTransaction")
                        db.requestDone();
                    }
                }
            }

            //} catch (Exception e) {
            //    logMe("Writer thread %d : EXCEPTION",threadNumber);
            //    e.printStackTrace();
            //}
            
            globalWriterThreads.decrementAndGet();
        }
    }
    
    
    public static String sysbenchString(java.util.Random rand, String thisMask) {
        String returnString = "";
        for (int i = 0, n = thisMask.length() ; i < n ; i++) { 
            char c = thisMask.charAt(i); 
            if (c == '#') {
                returnString += String.valueOf(rand.nextInt(10));
            } else if (c == '@') {
                returnString += (char) (rand.nextInt(26) + 'a');
            } else {
                returnString += c;
            }
        }
        return returnString;
    }

    class MyProfiler implements Runnable {
        DB db;
        
    	MyProfiler(DB db) {
            this.db = db;
    	}
    	
    	public void run() {
    		
    		long sleepTime = (1000 * 3600); // Wake up hourly
    		long interation = 0;
    		
    		CommandResult buildInfo = db.getSisterDB("admin").command("buildInfo");
    		
    		String complierFlags = buildInfo.getString("compilerFlags");
    		
    		if ( complierFlags.contains("use-cpu-profiler") ) {
    		
	    		BasicDBObject stopProfile = new BasicDBObject(); 
	    		stopProfile.put("_cpuProfilerStop", 1);
	    		
	    		BasicDBObject filename = new BasicDBObject();
	    		
	    		BasicDBObject startProfile = new BasicDBObject(); 
	    		startProfile.put("_cpuProfilerStart", filename);
	    		
	    		String profile_file = logFileName;
	    		profile_file = profile_file.replace(".txt.tsv", "-%03d.prof");
	    		
	    		while ( true ) {
	    			interation++;
	    			try {
						Thread.sleep(sleepTime);
	
						filename.put("profileFilename", String.format(profile_file, interation));
						db.getSisterDB("admin").command(startProfile);
						Thread.sleep(1000 * 30); // Lets the CPU gather for 30 seconds
	    			
						db.getSisterDB("admin").command(stopProfile);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	    		}
    		}
		}
    }

    // reporting thread, outputs information to console and file
    class MyReporter implements Runnable {
        public void run()
        {
            long t0 = System.currentTimeMillis();
            long lastInserts = 0;
            long thisInserts = 0;
            long lastDeletes = 0;
            long thisDeletes = 0;
            long lastUpdates = 0;
            long thisUpdates = 0;
            long lastPointQueries = 0;
            long thisPointQueries = 0;
            long lastRangeQueries = 0;
            long thisRangeQueries = 0;
            long lastSysbenchTransactions = 0;
            long thisSysbenchTransactions = 0;
            long lastMs = t0;
            long intervalNumber = 0;
            long nextFeedbackMillis = t0 + (1000 * secondsPerFeedback * (intervalNumber + 1));
            long sleepTime = (1000 * secondsPerFeedback) - 100;
            long runEndMillis = Long.MAX_VALUE;
            if (runSeconds > 0)
                runEndMillis = t0 + (1000 * runSeconds);
            
            while ((System.currentTimeMillis() < runEndMillis) && (thisInserts < numMaxInserts))
            {
                try {
                    //Thread.sleep(100);
                	// Sleep until we are due to wake up for next report
                	Thread.sleep(sleepTime);
                	sleepTime = 100;
                } catch (Exception e) {
                    e.printStackTrace();
                }
                
                long now = System.currentTimeMillis();
                
                
//    public static AtomicLong globalDeletes = new AtomicLong(0);
//    public static AtomicLong globalUpdates = new AtomicLong(0);
//    public static AtomicLong globalPointQueries = new AtomicLong(0);
//    public static AtomicLong globalRangeQueries = new AtomicLong(0);

                
                thisInserts = globalInserts.get();
                thisSysbenchTransactions = globalSysbenchTransactions.get();
                
                if ((now > nextFeedbackMillis) && (secondsPerFeedback > 0))
                {
                    intervalNumber++;
                    nextFeedbackMillis = t0 + (1000 * secondsPerFeedback * (intervalNumber + 1));
                    sleepTime = (1000 * secondsPerFeedback) -100;
                    long elapsed = now - t0;
                    long thisIntervalMs = now - lastMs;

                    long thisIntervalSysbenchTransactions = thisSysbenchTransactions - lastSysbenchTransactions;
                    double thisIntervalSysbenchTransactionsPerSecond = thisIntervalSysbenchTransactions/(double)thisIntervalMs*1000.0;
                    double thisSysbenchTransactionsPerSecond = thisSysbenchTransactions/(double)elapsed*1000.0;

                    long thisIntervalInserts = thisInserts - lastInserts;
                    double thisIntervalInsertsPerSecond = thisIntervalInserts/(double)thisIntervalMs*1000.0;
                    double thisInsertsPerSecond = thisInserts/(double)elapsed*1000.0;
                    
                    logMe("%,d seconds : cum tps=%,.2f : int tps=%,.2f : cum ips=%,.2f : int ips=%,.2f", elapsed / 1000l, thisSysbenchTransactionsPerSecond, thisIntervalSysbenchTransactionsPerSecond, thisInsertsPerSecond, thisIntervalInsertsPerSecond);
                    
                    try {
                        if (outputHeader)
                        {
                            writer.write("elap_secs\tcum_tps\tint_tps\tcum_ips\tint_ips\n");
                            outputHeader = false;
                        }
                            
                        String statusUpdate = "";
                        
                        statusUpdate = String.format("%d\t%.2f\t%.2f\t%.2f\t%.2f\n", elapsed / 1000l, thisSysbenchTransactionsPerSecond, thisIntervalSysbenchTransactionsPerSecond, thisInsertsPerSecond, thisIntervalInsertsPerSecond);
                            
                        writer.write(statusUpdate);
                        writer.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    lastInserts = thisInserts;
                    lastSysbenchTransactions = thisSysbenchTransactions;

                    lastMs = now;
                }
            }
            
            // shutdown all the writers
            allDone = 1;
        }
    }


    public static void logMe(String format, Object... args) {
        System.out.println(Thread.currentThread() + String.format(format, args));
    }
}

