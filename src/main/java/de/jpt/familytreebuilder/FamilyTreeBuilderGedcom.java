package de.jpt.familytreebuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;

import org.gedcom4j.exception.GedcomParserException;
import org.gedcom4j.factory.Sex;
import org.gedcom4j.model.AbstractEvent;
import org.gedcom4j.model.Family;
import org.gedcom4j.model.FamilyEvent;
import org.gedcom4j.model.FamilySpouse;
import org.gedcom4j.model.Gedcom;
import org.gedcom4j.model.Individual;
import org.gedcom4j.model.IndividualEvent;
import org.gedcom4j.model.IndividualReference;
import org.gedcom4j.model.PersonalName;
import org.gedcom4j.model.Place;
import org.gedcom4j.model.enumerations.IndividualEventType;
import org.gedcom4j.parser.GedcomParser;

public class FamilyTreeBuilderGedcom {
	
	private Gedcom gedcom;
	private PrintStream output;
	private String sep;

	/**
	 * 
	 * @param separator field separator, eg ; or \t
	 * @param output
	 */
	public FamilyTreeBuilderGedcom(String separator, PrintStream output) {
		this.sep = separator;
		this.output = output;
	}
	
	public void importFromGedcom(String filename) throws Exception, GedcomParserException {		
		GedcomParser parser = new GedcomParser();
	    parser.setStrictCustomTags(false);
//      parser.setStrictLineBreaks(false);
	    parser.load(filename);	        
		for (String s : parser.getWarnings()) {
			System.err.println("W "+s);
		}

		for (String s : parser.getErrors()) {
			System.err.println("E "+s);
		}
		gedcom = parser.getGedcom();
	}
	
	public Individual findIndividualById(String id) {
		return gedcom.getIndividuals().get('@'+id+'@');
	}
	
	/**
	 * Traverse Tree from Individual
	 * @param indi
	 */
	public void writeDescendantTreeOf(Individual indi, StringBuffer indent) {
		printIndividual(indi, indent);
		
		for (FamilySpouse familySpouse : indi.getFamiliesWhereSpouse(true)) {
			Family family = familySpouse.getFamily();
			printMarriage(family, indent.toString());
			if (indi.getSex() == null) {
				System.err.println("Unknown Sex of Spouse: "+ indi.getRecIdNumber()); 
			} else if (indi.getSex().toString().equals(Sex.FEMALE.getCode())) {
				printIndividual(family.getHusband(), indent); 
			} else if (indi.getSex().toString().equals(Sex.MALE.getCode())) {
				printIndividual(family.getWife(), indent); 
			} else {
				System.err.println("Unknown Sex of Spouse: "+ indi.getRecIdNumber()+", "+indi.getSex()); 
			}
			
			indent.append("  ");
			for (IndividualReference indiRef : family.getChildren(true)) {
				Individual child = indiRef.getIndividual();
//				printIndividual(child, indent);
				writeDescendantTreeOf(child, indent);
			}
			
			indent.setLength(indent.length()-2);
		}
	}	

	private void printMarriage(Family family, String indent) {
		StringBuffer text = new StringBuffer(50);
		text.append(indent);		
		text.append("oo"+sep);		
		formatMarriageEvent(text, family.getEvents(true));
		output.println(text);
	}

	private void printIndividual(IndividualReference indi, StringBuffer indent) {
		if (indi != null) {
			printIndividual(indi.getIndividual(), indent);
		} else {
			output.println(indent + "Unknown");
		}
	}

	private void printIndividual(Individual indi, StringBuffer indent) {
		StringBuffer text = new StringBuffer();
		text.append(indent);
		text.append(indi.getSex());
		text.append(": ");
		formatName(text, indi.getNames());

		text.append(sep+"*");
		formatIndividualEvent(text, indi.getEventsOfType(IndividualEventType.BIRTH));
		
		text.append(sep+"+");		
		formatIndividualEvent(text, indi.getEventsOfType(IndividualEventType.DEATH));
		//text.append("\"");		
		
		output.println(text);	
	}
	
	private void formatIndividualEvent(StringBuffer text, List<IndividualEvent> events) {
		for (AbstractEvent event : events) {
			if (event.getDate() != null) {
				text.append(event.getDate());
			} else {
				text.append('?');
			}
			formatPlace(text, event.getPlace());
			break;
		}
	}
	
	private void formatMarriageEvent(StringBuffer text, List<FamilyEvent> events) {
		for (FamilyEvent event : events) {
			text.append(event.getDate());
			formatPlace(text, event.getPlace());
		}
		
	}

	private void formatPlace(StringBuffer text, Place place) {
		if (place!= null) {
			text.append(" in ");
			text.append(place.getPlaceName());
		}		
	}
	
	private void formatName(StringBuffer text, List<PersonalName> names) {
		for (PersonalName name : names) {
			text.append(name);			
//			text.append(name.toString().replaceAll("\"", "\\\""));			
		}		
	}	

	private static File changeExt(File file, String ext) {
		return new File(file.getPath().replaceFirst("\\.[a-zA-Z]+$", ext));
	}
	
	private void createHeader() {
		output.println("Name"+sep+"Geburt/Heirat"+sep+"Tod");
		//+sep+"Datum:"+sep+gedcom.getHeader().getDate());
	}
	
	public static void main(String[] args) {
		String sourcefile = "testdata/FamilieFranzI-V1.3.ged";
		try {			
			FamilyTreeBuilderGedcom ftb = new FamilyTreeBuilderGedcom(";", 
					new PrintStream(new FileOutputStream(changeExt(new File(sourcefile), ".cvs")))
			);
			ftb.importFromGedcom(sourcefile);
			ftb.createHeader();
			ftb.writeDescendantTreeOf(ftb.findIndividualById("I81"), new StringBuffer(20));
		}
		catch (Exception e){
			e.printStackTrace();
		}
	}

}
