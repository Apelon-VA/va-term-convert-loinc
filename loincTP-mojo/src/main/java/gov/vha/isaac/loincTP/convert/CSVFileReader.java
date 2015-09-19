/**
 * Copyright Notice
 *
 * This is a work of the U.S. Government and is not subject to copyright
 * protection in the United States. Foreign copyrights may apply.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package gov.vha.isaac.loincTP.convert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import org.apache.commons.io.input.BOMInputStream;
import com.opencsv.CSVReader;

/**
 * 
 * {@link CSVFileReader}
 *
 * Reads the CSV formatted release files of LOINC, and the custom release notes file
 * to extract the date and time information.
 * 
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
public class CSVFileReader extends LOINCReader
{
	String[] header;
	CSVReader reader;
	String version = null;
	String release = null;
	
	public CSVFileReader(File f) throws IOException
	{
		System.out.println("Using the data file " + f.getAbsolutePath());
		//Their new format includes the (optional) UTF-8 BOM, which chokes java for stupid legacy reasons.
		reader = new CSVReader(new BufferedReader(new InputStreamReader(new BOMInputStream(new FileInputStream(f)))));
		header = readLine();
		
		readReleaseNotes(f.getParentFile());
	}
	
	private void readReleaseNotes(File dataFolder) throws IOException
	{
		File relNotes  = null;
		for (File f : dataFolder.listFiles())
		{
			if (f.getName().toLowerCase().equals("loinc_releasenotes.txt"))
			{
				relNotes = f;
				break;
			}
		}
		if (relNotes.exists())
		{
			BufferedReader br = new BufferedReader(new FileReader(relNotes));
			String line = br.readLine();
			while (line != null)
			{
				if (line.contains("Version"))
				{
					String temp = line.substring(line.indexOf("Version") + "Version ".length());
					temp = temp.replace('|', ' ');
					version = temp.trim();
					
				}
				if (line.contains("Released"))
				{
					String temp = line.substring(line.indexOf("Released") + "Released ".length());
					temp = temp.replace('|', ' ');
					release = temp.trim();
					break;
				}
				line = br.readLine();
			}
			br.close();
		}
		else
		{
			System.err.println("Couldn't find release notes file - can't read version or release date!");
		}
	}
	
	@Override
	public String getVersion()
	{
		return version;
	}

	@Override
	public String getReleaseDate()
	{
		return release;
	}

	@Override
	public String[] getHeader()
	{
		return header;
	}

	@Override
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

	@Override
	public void close() throws IOException
	{
		reader.close();
	}

}
