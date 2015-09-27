package gov.vha.isaac.loincTP.convert;

import gov.vha.isaac.ochre.api.Get;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.maven.plugin.logging.Log;

public class StatsCounter
{
	private int metadataConceptCounter;
	private int conceptCounter;
	
	private HashMap<Integer, AtomicInteger> descCounter = new HashMap<>();
	private HashMap<Integer, AtomicInteger> metadataDescCounter = new HashMap<>();
	
	private HashMap<Integer, AtomicInteger> attrCounter = new HashMap<>();
	private HashMap<Integer, AtomicInteger> metadataAttrCounter = new HashMap<>();
	
	private boolean metadataDone = false;
	
	private Log log_;
	
	public StatsCounter(Log log)
	{
		log_ = log;
	}
	
	public void addConcept()
	{
		if (metadataDone)
		{
			conceptCounter++;
		}
		else
		{
			metadataConceptCounter++;
		}
	}
	
	public void addDescription(int descriptionTypeConSequence)
	{
		if (metadataDone)
		{
			add(descCounter, descriptionTypeConSequence);
		}
		else
		{
			add(metadataDescCounter, descriptionTypeConSequence);
		}
	}
	
	public void addAttribute(int attributeTypeSequence)
	{
		if (metadataDone)
		{
			add(attrCounter, attributeTypeSequence);
		}
		else
		{
			add(metadataAttrCounter, attributeTypeSequence);
		}
	}
	
	public void metadataDone()
	{
		log_.info("Metadata Creation Completed");
		printStats();
		metadataDone = true;
	}
	
	public void printStats()
	{
		log_.info("Created " + (metadataDone ? conceptCounter : metadataConceptCounter) + " concepts");
		log_.info("Description Stats:");
		log_.info("Total " +  printStats(metadataDone ? descCounter : metadataDescCounter));
		log_.info("Attribute Stats:");
		log_.info("Total " +  printStats(metadataDone ? attrCounter : metadataAttrCounter));
	}
	
	private int printStats(HashMap<Integer, AtomicInteger> store)
	{
		int total = 0;
		for (Entry<Integer, AtomicInteger> entry : store.entrySet())
		{
			total += entry.getValue().get();
			log_.info(Get.conceptDescriptionText(entry.getKey()) + " : " + entry.getValue().get());
		}
		return total;
	}
	
	private void add(HashMap<Integer, AtomicInteger> store, int typeConceptSequence)
	{
		AtomicInteger ai = store.get(typeConceptSequence);
		if (ai == null)
		{
			ai = new AtomicInteger();
			store.put(typeConceptSequence, ai);
		}
		ai.incrementAndGet();
		
	}
}