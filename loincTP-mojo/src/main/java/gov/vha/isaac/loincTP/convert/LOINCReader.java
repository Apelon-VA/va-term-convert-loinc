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

import java.io.IOException;
import java.util.Hashtable;

/**
 * 
 * {@link LOINCReader}
 *
 * Abstract class for the required methods of a LOINC reader - we have several, as the format has changed
 * with each release, sometimes requiring a new parser.
 * 
 * @author <a href="mailto:daniel.armbrust.list@gmail.com">Dan Armbrust</a>
 */
public abstract class LOINCReader
{
	public abstract String getVersion();
	public abstract String getReleaseDate();
	public abstract String[] getHeader();
	public abstract String[] readLine() throws IOException;
	public abstract void close() throws IOException;
	
	protected int fieldCount_ = 0;
	protected Hashtable<String, Integer> fieldMap_ = new Hashtable<String, Integer>();
	protected Hashtable<Integer, String> fieldMapInverse_ = new Hashtable<Integer, String>();
	
	public Hashtable<String, Integer> getFieldMap()
	{
		return fieldMap_;
	}
	
	public Hashtable<Integer, String> getFieldMapInverse()
	{
		return fieldMapInverse_;
	}
}
