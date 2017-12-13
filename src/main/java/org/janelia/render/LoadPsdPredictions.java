package org.janelia.render;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;


//import com.google.gson.Gson;
//import com.google.gson.JsonSyntaxException;
//import com.google.gson.stream.JsonReader;


public class LoadPsdPredictions
{

	public static void main( String[] args ) throws IOException
	{
//		Gson gson;
//		final ObjectInputStream stream;
//		stream.defaultReadObject();
//		TbarPredictions params = new Gson().fromJson( IOUtils.toString(stream), TbarPredictions.class );

		String jsonData = "";
		BufferedReader br = null;
		try {
			String line;
			br = new BufferedReader(new FileReader("/data-ssd/john/flyem/small_tbars2.json"));
			System.out.println("reading");
			while ((line = br.readLine()) != null) {
				jsonData += line + "\n";
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)
					br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		System.out.println("done");

		JSONObject obj = new JSONObject(jsonData);
		JSONArray data = obj.getJSONArray("data");

//		Tbar tbar = Tbar.load( (JSONObject)data.get( 0 ) );
//		System.out.println( tbar.status );
		
		ArrayList< Tbar > tbars = Tbar.loadAll( data );
		System.out.println( tbars);

		
		System.out.println( "done" );
	}
	

	public static class Tbar
	{
		public String status;
		public double confidence;
		public int body_ID;
		public int[] location;
		
		public static Tbar load( JSONObject obj )
		{
			Tbar out = new Tbar();
			JSONObject tbar = (JSONObject)obj.get( "T-bar" );
			
			out.status = tbar.getString( "status" );
			out.confidence = tbar.getDouble( "confidence" );
			out.body_ID = tbar.getInt( "body ID" );
			JSONArray locArray = (JSONArray)tbar.get( "location" );
			out.location = new int[ 3 ];
			out.location[ 0 ] = locArray.getInt( 0 );
			out.location[ 1 ] = locArray.getInt( 1 );
			out.location[ 2 ] = locArray.getInt( 2 );

			return out;
		}
		
		public static ArrayList<Tbar> loadAll( JSONArray tbarArray )
		{
			ArrayList<Tbar> tbars = new ArrayList<Tbar>();
			for( Object obj : tbarArray )
			{
				tbars.add( Tbar.load((JSONObject) obj));
			}
			return tbars;
		}
	}

}
