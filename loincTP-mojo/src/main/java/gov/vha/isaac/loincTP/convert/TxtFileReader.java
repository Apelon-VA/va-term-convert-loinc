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
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

/**
 * 
 * {@link TxtFileReader}
 * 
 * A reader for various txt file formats used by LOINC.
 * 
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
public class TxtFileReader extends LOINCReader
{
	BufferedReader dataReader = null;

	String version;
	String releaseDate;
	String headerLine;
	

	public TxtFileReader(File f) throws Exception
	{
		System.out.println("Using the data file " + f.getAbsolutePath());
		dataReader = new BufferedReader(new FileReader(f));
		// Line 1 of the file is version, line 2 is date. Hope they are consistent.....
		version = dataReader.readLine();
		releaseDate = dataReader.readLine();

		// Scan forward in the data file for the "cutoff" point
		int i = 0;
		while (true)
		{
			i++;
			String temp = dataReader.readLine();
			if (temp.equals("<----Clip Here for Data----->"))
			{
				break;
			}
			if (i > 500)
			{
				throw new Exception("Couldn't find '<----Clip Here for Data----->' constant.  Format must have changed.  Failing");
			}
		}
		
		headerLine = dataReader.readLine();
	}
	
	

	@Override
	public String getVersion()
	{
		return version;
	}

	@Override
	public String getReleaseDate()
	{
		return releaseDate;
	}

	@Override
	public String[] getHeader()
	{
		return getFields(headerLine);
	}

	@Override
	public String[] readLine() throws IOException
	{
		String line = dataReader.readLine();
		if (line != null && line.length() > 0)
		{
			return getFields(line);
		}
		return null;
	}

	@Override
	public void close() throws IOException
	{
		dataReader.close();
	}
	
	private String[] getFields(String line)
	{
		String[] temp = line.split("\\t");
		for (int i = 0; i < temp.length; i++)
		{
			if (temp[i].length() == 0)
			{
				temp[i] = null;
			}
			else if (temp[i].startsWith("\"") && temp[i].endsWith("\""))
			{
				temp[i] = temp[i].substring(1, temp[i].length() - 1);
			}
		}
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
			throw new RuntimeException("Data error - to many fields found on line: " + line);
		}
		return temp;
	}
}
