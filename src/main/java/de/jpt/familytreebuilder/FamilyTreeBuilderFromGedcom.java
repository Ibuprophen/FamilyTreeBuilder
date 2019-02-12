package de.jpt.familytreebuilder;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.List;

import org.gedcom4j.exception.GedcomParserException;
import org.gedcom4j.model.Family;
import org.gedcom4j.model.FamilyEvent;
import org.gedcom4j.model.FamilySpouse;
import org.gedcom4j.model.Gedcom;
import org.gedcom4j.model.Individual;
import org.gedcom4j.model.IndividualEvent;
import org.gedcom4j.model.IndividualReference;
import org.gedcom4j.model.enumerations.IndividualEventType;
import org.gedcom4j.parser.GedcomParser;

public class FamilyTreeBuilderFromGedcom {
	
	private static final char NEWLINE = '\n';
	private static final char SPACER = ' ';
	private Gedcom gedcom;
	private String sep;

	public FamilyTreeBuilderFromGedcom(String separator) {
		sep = separator;
	}	
	
	public void importFromGedcom(String file) throws IOException, GedcomParserException {		
		GedcomParser parser = new GedcomParser();
	    parser.setStrictCustomTags(false);
	    parser.load(file);	        
		for (String s : parser.getWarnings()) {
			System.err.println("W "+s);
		}

		for (String s : parser.getErrors()) {
			System.err.println("E "+s);
		}
		
		gedcom = parser.getGedcom();
	}

	public void printTreeFrom(PrintStream output, Individual indi, String indent) {
		
		printIndi(output, indi, indent);
		
		String eheCounter = "";
		for (FamilySpouse familySpouse : indi.getFamiliesWhereSpouse(true)) {
			Family family = familySpouse.getFamily();
//			indent+="  ";
			printMarriage(output, family.getEvents(true), indent+"  ", eheCounter);
			
			try {
				Individual spouse;
				if (family.getHusband().getIndividual().equals(indi)) {
					spouse = family.getWife().getIndividual();
				} else if (family.getWife().getIndividual().equals(indi)) {				
					spouse = family.getHusband().getIndividual();
				} else {
					// Gleichgeschlechtliche Ehe?
					throw new RuntimeException("Illegal Family:"+family);
				}
				printIndi(output, spouse, indent+"  ");
			} catch (Exception e) {
				output.append(SPACER);
				output.append(sep);
				output.append(indent);
				output.println("    NN");
			}
			
//			indent+="  ";
			for (IndividualReference childRef : family.getChildren(true)) {
				printTreeFrom(output, childRef.getIndividual(), indent+"    ");				
			}
			if ("".contentEquals(eheCounter)) {
				eheCounter = "II";
			} else {
				eheCounter += 'I';
			}
		}
	}		
	
	private void printMarriage(PrintStream output, List<FamilyEvent> marriages, String indent, String eheCounter) {
		output.append(SPACER);
		output.append(sep);
		output.append(indent);
		output.append("oo");
		output.append(eheCounter);
		for (FamilyEvent m : marriages) {
			output.append(sep);
			output.append("oo");
			output.append(SPACER);
			try {
				output.append(m.getDate().getValue());
			} catch (NullPointerException e) {
//				output.append("MD-NPE");
			}
			output.append(SPACER);
			try {
				output.append(m.getPlace().getPlaceName());
			} catch (NullPointerException e) {
//				output.append("MP-NPE");
			}
			break;
		}		
		output.append(NEWLINE);
	}	
	
	private void printEvent(PrintStream output, List<IndividualEvent> events, List<IndividualEvent> altEvents) {
		for (IndividualEvent e : events) {
			try {
				output.append(e.getDate().getValue());
			} catch (NullPointerException x) {
//				output.append("D-NPE");
				try {
					output.append(altEvents.get(0).getDate().getValue());
				} catch (IndexOutOfBoundsException | NullPointerException x2) {
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
			output.append(SPACER);
			try {
				output.append(e.getPlace().getPlaceName());
			} catch (NullPointerException x) {
//				output.append("P-NPE");
				try {
					output.append(altEvents.get(0).getPlace().getPlaceName());
				} catch (IndexOutOfBoundsException | NullPointerException x2) {
				} catch (Throwable t) {
					t.printStackTrace();
				}
			}
			return; // print only first
		}
	}

	private void printIndi(PrintStream output, Individual indi, String indent) {

		output.append(indi.getSex().toString());
		output.append(sep);
		output.append(indent);
		output.append(indi.getNames(true).get(0).toString());

		output.append(sep);
		output.append('*');		
		output.append(SPACER);
		printEvent(output, indi.getEventsOfType(IndividualEventType.BIRTH), indi.getEventsOfType(IndividualEventType.BAPTISM));
		
		output.append(sep);
		output.append('+');		
		output.append(SPACER);
		printEvent(output, indi.getEventsOfType(IndividualEventType.DEATH), indi.getEventsOfType(IndividualEventType.BURIAL));

		output.append(NEWLINE);
	}

	private Individual findIndiById(String id) {
		return gedcom.getIndividuals().get('@'+id+'@');
	}
	
	public static void main(String[] args) {
		try {
			String sep = ";";
			String filename = "FamilieFranzI-V2.1";
			FamilyTreeBuilderFromGedcom ftb = new FamilyTreeBuilderFromGedcom(sep);
			ftb.importFromGedcom("testdata/"+filename+".ged");
			ftb.printTreeFrom(System.out, ftb.findIndiById("I81"), "");		
			PrintStream output = new PrintStream(new File("testdata/"+filename+".csv"));
			System.out.println(new SimpleDateFormat("dd MMM yyy").format(new File("testdata/"+filename+".ged").lastModified())+sep+filename);
			output.println(new SimpleDateFormat("dd MMM yyy").format(new File("testdata/"+filename+".ged").lastModified())+sep+filename);
			output.println(";Name;Geburt/Ehe;Tod;");
			ftb.printTreeFrom(output, ftb.findIndiById("I81"), "");
		} catch (Throwable e) {
			e.printStackTrace();
		}		
	}

}
