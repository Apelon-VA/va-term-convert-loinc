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
package com.apelon.akcds.loinc.propertyTypes;

import gov.va.oia.terminology.converters.sharedUtils.propertyTypes.BPT_IDs;


/**
 * Fields to treat as IDs
 * @author Daniel Armbrust
 */
public class PT_IDs extends BPT_IDs
{
	public PT_IDs()
	{
		super();
		addProperty("LOINC_NUM");
		
		//Abbrev Codes used by axis and class
		addProperty("ABBREVIATION");
		
		//From multi-axial class
		addProperty("CODE");
		
	}
}
