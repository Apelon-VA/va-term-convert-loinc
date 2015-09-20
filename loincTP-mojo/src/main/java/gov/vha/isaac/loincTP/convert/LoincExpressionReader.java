package gov.vha.isaac.loincTP.convert;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.apache.commons.io.input.BOMInputStream;
import com.opencsv.CSVReader;

public class LoincExpressionReader
{
	String[] header;
	CSVReader reader;
	ZipFile zip;
	
	protected int fieldCount_ = 0;
	protected Hashtable<String, Integer> fieldMap_ = new Hashtable<String, Integer>();
	protected Hashtable<Integer, String> fieldMapInverse_ = new Hashtable<Integer, String>();
	
	public LoincExpressionReader(File zipFile) throws ZipException, IOException
	{
		zip = new ZipFile(zipFile);
		Enumeration<? extends ZipEntry> entries = zip.entries();
		
		boolean found = false;
		while (entries.hasMoreElements())
		{
			ZipEntry ze = entries.nextElement();
			if (ze.getName().toLowerCase().contains("xder2_sscccRefset_LOINCExpressionAssociationFull"))
			{
				found = true;
				init(zip.getInputStream(ze));
				break;
			}
		}
		if (!found)
		{
			throw new IOException("Unable to find expression refset file with the pattern 'xder2_sscccRefset_LOINCExpressionAssociationFull' in the zip file " 
					+ zipFile.getAbsolutePath());
		}
	}
	
	public LoincExpressionReader(InputStream is) throws IOException
	{
		init(is);
	}
	
	private void init(InputStream is) throws IOException
	{
		reader = new CSVReader(new BufferedReader(new InputStreamReader(new BOMInputStream(is))));
		header = readLine();
	}
	
	public String[] readLine() throws IOException
	{
		String[] temp = reader.readNext();
		if (temp != null)
		{
			if (fieldCount_ == 0)
			{
				fieldCount_ = temp.length;
				int i = 0;
				for (String s : temp)
				{
					fieldMapInverse_.put(i, s);
					fieldMap_.put(s, i++);
				}
			}
			else if (temp.length < fieldCount_)
			{
				temp = Arrays.copyOf(temp, fieldCount_);
			}
			else if (temp.length > fieldCount_)
			{
				throw new RuntimeException("Data error - to many fields found on line: " + Arrays.toString(temp));
			}
		}
		return temp;
	}
	
	public String[] getHeader()
	{
		return header;
	}
	
	public int getPositionForColumn(String col)
	{
		return fieldMap_.get(col);
	}
	
	public void close() throws IOException
	{
		reader.close();
		zip.close();
	}
}
