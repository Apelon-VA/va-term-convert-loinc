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

import static gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder.And;
import static gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder.ConceptAssertion;
import static gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder.NecessarySet;
import gov.vha.isaac.expression.parser.ISAACVisitor;
import gov.vha.isaac.metadata.coordinates.EditCoordinates;
import gov.vha.isaac.metadata.coordinates.LogicCoordinates;
import gov.vha.isaac.metadata.source.IsaacMetadataAuxiliaryBinding;
import gov.vha.isaac.mojo.external.QuasiMojo;
import gov.vha.isaac.ochre.api.ConceptProxy;
import gov.vha.isaac.ochre.api.Get;
import gov.vha.isaac.ochre.api.LookupService;
import gov.vha.isaac.ochre.api.Util;
import gov.vha.isaac.ochre.api.chronicle.IdentifiedObjectLocal;
import gov.vha.isaac.ochre.api.commit.ChangeCheckerMode;
import gov.vha.isaac.ochre.api.component.concept.ConceptBuilder;
import gov.vha.isaac.ochre.api.component.concept.ConceptBuilderService;
import gov.vha.isaac.ochre.api.component.concept.ConceptChronology;
import gov.vha.isaac.ochre.api.component.concept.description.DescriptionBuilder;
import gov.vha.isaac.ochre.api.component.concept.description.DescriptionBuilderService;
import gov.vha.isaac.ochre.api.component.sememe.SememeBuilder;
import gov.vha.isaac.ochre.api.component.sememe.SememeBuilderService;
import gov.vha.isaac.ochre.api.component.sememe.SememeChronology;
import gov.vha.isaac.ochre.api.component.sememe.version.MutableDescriptionSememe;
import gov.vha.isaac.ochre.api.logic.LogicalExpression;
import gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilder;
import gov.vha.isaac.ochre.api.logic.LogicalExpressionBuilderService;
import gov.vha.isaac.ochre.api.task.TimedTask;
import gov.vha.isaac.ochre.util.UuidT5Generator;
import gov.vha.isaac.ochre.util.WorkExecutors;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import javafx.util.Pair;
import org.antlr.v4.runtime.tree.ParseTree;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.jvnet.hk2.annotations.Service;
import se.liu.imt.mi.snomedct.expression.tools.SNOMEDCTParserUtil;

/**
 * 
 * Loader code to convert Loinc into the workbench.
 * 
 * Paths are typically controlled by maven, however, the main() method has paths configured so that they
 * match what maven does for test purposes.
 */
@Service(name = "load-loinc-tech-preview")
public class LoincTPLoaderMojo extends QuasiMojo
{
	//TODO more stats on descriptions, sememes, etc.

	/**
	 * Location of the input source file(s). May be a file or a directory, depending on the specific loader.
	 * Usually a directory.
	 */
	protected File loincFileLocation;

	/**
	 * Location of the input source file(s). May be a file or a directory, depending on the specific loader.
	 * Usually a directory.
	 */
	protected File loincTPFileLocation;

	/**
	 * Loader version number
	 */
	protected String loaderVersion;

	private static final String necessarySctid = "900000000000074008";
	private static final String sufficientSctid = "900000000000073002";

	@Override
	public void execute() throws MojoExecutionException
	{
		getLog().info("LOINC Tech Preview Processing Begins " + new Date().toString());
		
		TimedTask<Void> task = new LoincWorker();
		LookupService.getService(WorkExecutors.class).getExecutor().submit(task);
		try
		{
			Util.addToTaskSetAndWaitTillDone(task);
		}
		catch (InterruptedException | ExecutionException e)
		{
			if (task.getException() != null && task.getException() instanceof MojoExecutionException)
			{
				throw (MojoExecutionException)task.getException();
			}
			throw new MojoExecutionException("Failure", e);
		}

		getLog().info("LOINC Tech Preview Processing Ends " + new Date().toString());
	}

	private class LoincWorker extends TimedTask<Void>
	{

		@Override
		protected Void call() throws Exception
		{
			getLog().info("Processing LOINC");
			updateTitle("Processing LOINC");
			updateMessage("Reading Data Files");
			updateProgress(1, 7);
			LOINCReader loincData = null;

			try
			{
				if (!loincFileLocation.isDirectory())
				{
					throw new MojoExecutionException("loincFileLocation must point to a directory containing the required loinc data files");
				}

				if (!loincTPFileLocation.isDirectory())
				{
					throw new MojoExecutionException("loincTPFileLocation must point to a directory containing the required loinc data files");
				}

				for (File f : loincFileLocation.listFiles())
				{
					if (f.getName().toLowerCase().equals("loinc.csv"))
					{
						loincData = new LoincCsvFileReader(f);
					}
				}

				if (loincData == null)
				{
					throw new MojoExecutionException("Could not find the loinc data file in " + loincFileLocation.getAbsolutePath());
				}

//				SimpleDateFormat dateReader = new SimpleDateFormat("MMMMMMMMMMMMM yyyy"); //Parse things like "June 2014"
//				Date releaseDate = dateReader.parse(loincData.getReleaseDate());

				String version = loincData.getVersion();
				
				init();

				getLog().info("Loading Metadata");
				updateMessage("Loading Metadata");
				updateProgress(2, 7);

				
				ConceptChronology<?> metadataRoot = createConcept("LOINC Metadata", "ISAAC", "LOINC Metadata", null, 
						IsaacMetadataAuxiliaryBinding.ISAAC_ROOT.getConceptSequence());
				createSememeStringAnnotation(version, null, metadataRoot.getNid(), IsaacMetadataAuxiliaryBinding.CONTENT_SOURCE_VERSION.getConceptSequence());
				createSememeStringAnnotation(loincData.getReleaseDate(), null, metadataRoot.getNid(), IsaacMetadataAuxiliaryBinding.CONTENT_RELEASE_DATE.getConceptSequence());
				createSememeStringAnnotation(loaderVersion, null, metadataRoot.getNid(), IsaacMetadataAuxiliaryBinding.LOADER_VERSION.getConceptSequence());
				createSememeStringAnnotation(projectVersion, null, metadataRoot.getNid(), IsaacMetadataAuxiliaryBinding.ARTIFACT_VERSION.getConceptSequence());

				ConceptChronology<?> attributesRoot = createConcept("Attribute Types", "ISAAC", "Attribute Types", null, 
						metadataRoot.getConceptSequence());
				
				HashMap<String, ConceptChronology<?>> attributeTypes = new HashMap<>();  //col name to concept sequence
				attributeTypes.put("FORMULA", createConcept("FORMULA", null, "Formula", null, attributesRoot.getConceptSequence()));
				attributeTypes.put("EXMPL_ANSWERS", createConcept("EXMPL_ANSWERS", null, "Example Answers", null, attributesRoot.getConceptSequence()));
				attributeTypes.put("RELATEDNAMES2", createConcept("RELATEDNAMES2", null, "Related Names", null, attributesRoot.getConceptSequence()));
				
				//TODO do I need any other attrs right now?

				// load the data
				getLog().info("Reading data file into memory.");
				updateMessage("Reading data file into memory");
				updateProgress(3, 7);
				int conCounter = 0;

				HashMap<String, String[]> loincNumToData = new HashMap<>();
				{
					String[] line = loincData.readLine();
					while (line != null)
					{
						if (line.length > 0)
						{
							loincNumToData.put(line[loincData.getFieldMap().get("LOINC_NUM")], line);
						}
						line = loincData.readLine();
						if (loincNumToData.size() % 1000 == 0)
						{
							updateTitle("Read " + loincNumToData.size() + " lines");
						}
					}
				}
				loincData.close();

				getLog().info("Read " + loincNumToData.size()  + " data lines from file");
				updateMessage("Read " + loincNumToData.size()  + " data lines from file");
				updateProgress(4, 7);
				
				File zipFile = null;
				for (File f : loincTPFileLocation.listFiles())
				{
					if (f.isFile() && f.getName().toLowerCase().endsWith(".zip"))
					{
						if (zipFile != null)
						{
							throw new RuntimeException("Found multiple zip files in " + loincTPFileLocation.getAbsolutePath());
						}
						zipFile = f;
					}
				}
				if (zipFile == null)
				{
					throw new RuntimeException("Couldn't find the tech preview zip file in " + loincTPFileLocation.getAbsolutePath());
				}
				
				/*
				 * Columns in this data file are:
				 * id - A UUID for this row
				 * effectiveTime
				 * active - 1 for active
				 * moduleId
				 * refsetId
				 * referencedComponentId
				 * mapTarget - LOINC_NUM
				 * Expression - the goods
				 * definitionStatusId
				 * correlationId
				 * contentOriginId
				 */
				
				getLog().info("Processing Expressions / Creating Concepts");
				updateMessage("Processing Expressions / Creating Concepts");
				updateProgress(5, 7);
				
				LoincExpressionReader ler = new LoincExpressionReader(zipFile);
				String[] expressionLine = ler.readLine();
				int lineCount = 1;
				while (expressionLine != null)
				{
					
					if (expressionLine.length > 0)
					{
						String[] loincConceptData = loincNumToData.get(expressionLine[ler.getPositionForColumn("mapTarget")]);
						
						if (loincConceptData == null)
						{
							getLog().warn("Skipping line " + lineCount + " because I can't find loincNum " + expressionLine[ler.getPositionForColumn("mapTarget")]);
						}
						
						boolean active = expressionLine[ler.getPositionForColumn("active")].equals("1");
						if (!active)
						{
							getLog().warn("Skipping line " + lineCount + " because it is inactive");
						}
						
						if (active && loincConceptData != null)
						{
							ParseTree parseTree;
							String definitionSctid = expressionLine[ler.getPositionForColumn("definitionStatusId")];
							if (definitionSctid.equals(sufficientSctid))
							{
								parseTree = SNOMEDCTParserUtil.parseExpression(expressionLine[ler.getPositionForColumn("Expression")]);
							}
							else if (definitionSctid.equals(necessarySctid))
							{
								//See <<< black magic from http://ihtsdo.org/fileadmin/user_upload/doc/download/doc_CompositionalGrammarSpecificationAndGuide_Current-en-US_INT_20150708.pdf?ok
								parseTree = SNOMEDCTParserUtil.parseExpression("<<< " + expressionLine[ler.getPositionForColumn("Expression")]);
							}
							else
							{
								throw new RuntimeException("Unexpected definition status: " + definitionSctid + " on line " + lineCount);
							}
	
							LogicalExpressionBuilder defBuilder = expressionBuilderService.getLogicalExpressionBuilder();
							ISAACVisitor visitor = new ISAACVisitor(defBuilder);
							visitor.visit(parseTree);
							LogicalExpression expression = defBuilder.build();
							
							
							//Build up a concept with the attributes we want, and the expression from the tech preview
							
							ConceptBuilder conBuilder = conceptBuilderService.getDefaultConceptBuilder(loincConceptData[loincData.getPositionForColumn("LONG_COMMON_NAME")], 
									null, null);  //don't put logic in here, can't set the UUID this way
							
							SememeBuilder<?> logicBuilder = sememeBuilderService.getLogicalExpressionSememeBuilder(expression, 
									conBuilder, LogicCoordinates.getStandardElProfile().getStatedAssemblageSequence());
							logicBuilder.setPrimordialUuid(UUID.fromString(expressionLine[ler.getPositionForColumn("id")]));  //expression id
							conBuilder.addLogicalDefinition(logicBuilder);
							
							String loincNum = loincConceptData[loincData.getPositionForColumn("LOINC_NUM")];
							conBuilder.setPrimordialUuid(makeNamespaceUUID(loincNum));  //Concept UUID
	
							ConceptChronology<?> newCon = conBuilder.build(EditCoordinates.getDefaultUserSolorOverlay(), ChangeCheckerMode.ACTIVE, new ArrayList<>());
							Get.commitService().addUncommitted(newCon);  //TODO is this needed?
							conCounter++;
							
							//add descriptions
							HashMap<String, Pair<ConceptProxy, ConceptProxy>> descCols = new HashMap<>();  //loinc name -> (desc type, desc subtype)
							
							descCols.put("CONSUMER_NAME", new Pair<>(IsaacMetadataAuxiliaryBinding.SYNONYM, IsaacMetadataAuxiliaryBinding.LOINC_CONSUMER_NAME));
							descCols.put("SHORTNAME", new Pair<>(IsaacMetadataAuxiliaryBinding.SYNONYM, IsaacMetadataAuxiliaryBinding.LOINC_SHORT_NAME));
							descCols.put("LONG_COMMON_NAME", new Pair<>(IsaacMetadataAuxiliaryBinding.SYNONYM, IsaacMetadataAuxiliaryBinding.LOINC_LONG_COMMON_NAME));
							descCols.put("DefinitionDescription", new Pair<>(IsaacMetadataAuxiliaryBinding.DEFINITION_DESCRIPTION_TYPE, 
									IsaacMetadataAuxiliaryBinding.LOINC_DEFINITION_DESCRIPTION));
							
							for (Entry<String, Pair<ConceptProxy, ConceptProxy>> desc : descCols.entrySet())
							{
								String data = loincConceptData[loincData.getPositionForColumn(desc.getKey())];
								if (!StringUtils.isBlank(data))
								{
									createDescription(data, 
											makeNamespaceUUID(desc.getKey() + ":" + newCon.getPrimordialUuid() + ":" + data),
											newCon.getConceptSequence(), desc.getValue().getKey(), desc.getValue().getValue(), 
											desc.getKey().equals("LONG_COMMON_NAME"));
								}
							}
							
							
							//add attributes
							for (Entry<String, ConceptChronology<?>> attrInfo : attributeTypes.entrySet())
							{
								String data = loincConceptData[loincData.getPositionForColumn(attrInfo.getKey())];
								if (!StringUtils.isBlank(data))
								{
									createSememeStringAnnotation(data, 
										makeNamespaceUUID(attrInfo.getKey() + ":" + newCon.getPrimordialUuid() + ":" + data),
										newCon.getNid(), attrInfo.getValue().getConceptSequence());
								}
							}
						}
					}
					
					expressionLine = ler.readLine();
					lineCount++;
				}

				getLog().info("Created " + conCounter + " concepts total");
				
				getLog().info("Committing");
				updateMessage("Committing");
				updateProgress(6, 7);
				Get.commitService().commit(EditCoordinates.getDefaultUserSolorOverlay(), "LOINC Creation commit");
				
				getLog().info("Finished");
				updateMessage("Finished");
				updateProgress(7, 7);
				
			}

			catch (Exception ex)
			{
				throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
			}
			finally
			{
				try
				{
					loincData.close();
				}
				catch (IOException e)
				{
					throw new RuntimeException("Failure", e);
				}
			}
			return null;
		}
	}
	
	ConceptBuilderService conceptBuilderService;
	DescriptionBuilderService descriptionBuilderService;
	LogicalExpressionBuilderService expressionBuilderService;
	SememeBuilderService<?> sememeBuilderService;
	
	private void init()
	{
		conceptBuilderService = LookupService.getService(ConceptBuilderService.class);
		conceptBuilderService.setDefaultLanguageForDescriptions(IsaacMetadataAuxiliaryBinding.ENGLISH);
		conceptBuilderService.setDefaultDialectAssemblageForDescriptions(IsaacMetadataAuxiliaryBinding.US_ENGLISH_DIALECT);
		conceptBuilderService.setDefaultLogicCoordinate(LogicCoordinates.getStandardElProfile());

		descriptionBuilderService = LookupService.getService(DescriptionBuilderService.class);
		expressionBuilderService = LookupService.getService(LogicalExpressionBuilderService.class);
		
		sememeBuilderService = Get.sememeBuilderService();
	}
	
	/**
	 * Create a new (ENGLISH) concept, add it (uncommitted)
	 * @param fsn - required
	 * @param semanticTag - optional
	 * @param preferredTerm - optional, uses FSN if not provided
	 * @param uuid - optional
	 * @param parent - required
	 * @return the created chronology
	 */
	private ConceptChronology<?> createConcept(String fsn, String semanticTag, String preferredTerm, UUID uuid, int parentNidOrSequence)
	{
		LogicalExpressionBuilder defBuilder = expressionBuilderService.getLogicalExpressionBuilder();
		NecessarySet(And(ConceptAssertion(Get.conceptService().getConcept(parentNidOrSequence), defBuilder)));
		LogicalExpression parentDef = defBuilder.build();

		ConceptBuilder builder = conceptBuilderService.getDefaultConceptBuilder(fsn, semanticTag, parentDef);
		if (uuid != null)
		{
			builder.setPrimordialUuid(uuid);
		}

		@SuppressWarnings("rawtypes") 
		DescriptionBuilder definitionBuilder = descriptionBuilderService.getDescriptionBuilder((StringUtils.isBlank(preferredTerm) ? fsn : preferredTerm), builder,
				IsaacMetadataAuxiliaryBinding.SYNONYM, IsaacMetadataAuxiliaryBinding.ENGLISH);
		definitionBuilder.setPreferredInDialectAssemblage(IsaacMetadataAuxiliaryBinding.US_ENGLISH_DIALECT);
		builder.addDescription(definitionBuilder);

		ConceptChronology<?> newCon = builder.build(EditCoordinates.getDefaultUserSolorOverlay(), ChangeCheckerMode.ACTIVE, new ArrayList<>());
		Get.commitService().addUncommitted(newCon);  //TODO is this needed?
		return newCon;
	}
	
	/**
	 * 
	 * @param value - required
	 * @param uuid - optional
	 * @param conceptSequence - required
	 * @param descriptionType - required, should be {@link IsaacMetadataAuxiliaryBinding#SYNONYM or DEFINITION or FULLY_SPECIFIED_NAME}
	 * @param subDescriptionType - optional
	 * @param preferred
	 */
	private SememeChronology<?> createDescription(String value, UUID uuid, int conceptSequence, ConceptProxy descriptionType, ConceptProxy subDescriptionType,
			boolean preferred)
	{
		DescriptionBuilder<? extends SememeChronology<?>, ? extends MutableDescriptionSememe<?>> db = descriptionBuilderService.getDescriptionBuilder(value,
				conceptSequence, descriptionType, IsaacMetadataAuxiliaryBinding.ENGLISH);
		if (uuid != null)
		{
			db.setPrimordialUuid(uuid);
		}

		sememeBuilderService.getComponentSememeBuilder((preferred ? IsaacMetadataAuxiliaryBinding.PREFERRED.getNid() : IsaacMetadataAuxiliaryBinding.ACCEPTABLE.getNid()),
				db, IsaacMetadataAuxiliaryBinding.US_ENGLISH_DIALECT.getConceptSequence())
				.build(EditCoordinates.getDefaultUserSolorOverlay(), ChangeCheckerMode.ACTIVE);

		if (subDescriptionType != null)
		{
			sememeBuilderService.getMembershipSememeBuilder(db, subDescriptionType.getConceptSequence()).build(EditCoordinates.getDefaultUserSolorOverlay(),
					ChangeCheckerMode.ACTIVE);
		}
		
		return db.build(EditCoordinates.getDefaultUserSolorOverlay(), ChangeCheckerMode.ACTIVE);
	}
	
	private IdentifiedObjectLocal createSememeStringAnnotation(String value, UUID uuid, int referencedComponentToAnnotate, int assemblageConceptSequence)
	{
		SememeBuilder<?> sb = sememeBuilderService.getStringSememeBuilder(value, referencedComponentToAnnotate, assemblageConceptSequence);
		if (uuid != null)
		{
			sb.setPrimordialUuid(uuid);
		}
		return sb.build(EditCoordinates.getDefaultUserSolorOverlay(), ChangeCheckerMode.ACTIVE);
		
	}
	
	private UUID makeNamespaceUUID(String value)
	{
		return UuidT5Generator.get(IsaacMetadataAuxiliaryBinding.LOINC.getPrimodialUuid(), value);
	}
}
