package de.jpt.familytreebuilder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;

import org.gedcom4j.model.Individual;
import org.gedcom4j.model.IndividualEvent;
import org.gedcom4j.model.PersonalName;
import org.gedcom4j.model.Place;
import org.gedcom4j.model.StringWithCustomFacts;
import org.gedcom4j.model.enumerations.IndividualEventType;

public class FamilyTreeBuilder implements AutoCloseable {
	
	private Connection conn;
	private PreparedStatement stmtGetPerson;
	private PreparedStatement stmtGetChildren;
	private PreparedStatement stmtGetWifes;
	private PreparedStatement stmtGetHusbands;

	public FamilyTreeBuilder(Connection conn, String gedcom) {
		this.conn = conn;
		
		try {
			stmtGetPerson = conn.prepareStatement(
					"select * from tng_people "
					+ "where personID=? and Gedcom = '" + gedcom + "'");
			
/*			stmtGetFamilies = conn.prepareStatement(
					"select * from tng_families "
					+ "where husbandID=? or wife=? and Gedcom = '" + gedcom + "'"  
					+ "order by marrdatetr");			
*/			
			stmtGetWifes = conn.prepareStatement(
					"select * from tng_families as f "
					+ "join tng_people as p on f.wife = p.personid and p.gedcom = f.gedcom "
					+ "where f.husband=? and f.Gedcom = '" + gedcom + "'"  
					+ "order by f.marrdatetr");			
			
			stmtGetHusbands = conn.prepareStatement(
					"select * from tng_families as f "
					+ "join tng_people as p on f.husband = p.personid and p.gedcom = f.gedcom "
					+ "where f.wife=? and f.Gedcom = '" + gedcom + "'"  
					+ "order by f.marrdatetr");			
				
			stmtGetChildren = conn.prepareStatement(
					"select * from tng_people as pc " + 
					"join tng_children as c on c.personID=pc.personID and c.gedcom = pc.gedcom " + 
					"join tng_families as fam on c.familyID=fam.familyID and c.gedcom = fam.gedcom " + 
					"where fam.familyid = ? and pc.gedcom = '" + gedcom + "'" + 
					"order by c.ordernum" ); 

		} catch (SQLException e) {
			e.printStackTrace();
			try {
				conn.close();
			} catch (Throwable ex) {
				ex.printStackTrace();
			}
		}
	}
	
	public void printTreeFrom(String personID, String indent) throws SQLException {
		stmtGetPerson.setString(1, personID);
		ResultSet rs = stmtGetPerson.executeQuery();
//		String indent = "";
		if (rs.next()) {
			ResultSet rsSpouse = null;
			printPerson(rs, indent);
			if (rs.getString("sex").equals("M")) {
				stmtGetWifes.setString(1, personID);
//				stmtGetWifes.setInt(2, generation);
				rsSpouse = stmtGetWifes.executeQuery();
			} else if (rs.getString("sex").equals("F")) {
				stmtGetHusbands.setString(1, personID);
//				stmtGetHusbands.setInt(2, generation);
				rsSpouse = stmtGetHusbands.executeQuery();
			}
			while (rsSpouse.next()) {
				printMarriage(rsSpouse, indent);
				printPerson(rsSpouse, indent);
				String familyID = rsSpouse.getString("f.FamilyID");
				stmtGetChildren.setString(1, familyID);
//				stmtGetChildren.setInt(2, generation);
				ResultSet rsChildren = stmtGetChildren.executeQuery();
				while (rsChildren.next()) {
//					printPerson(rsChildren, "  ");
					printTreeFrom(rsChildren.getString("PersonID"), indent+"  ");
				}
				rsChildren.close();
			}
			rsSpouse.close();		
		}
		rs.close();
	}
	
	public Individual importFromTng(String personID) throws SQLException {
		Individual root = null;

		stmtGetPerson.setString(1, personID);
		ResultSet rsRoot = stmtGetPerson.executeQuery();			
		if (rsRoot.next()) {
			root = createIndi(rsRoot);			
			rsRoot.close();
			
			root.getFamiliesWhereChild(true);			
			
			TreeNode<Individual> parent = new TreeNode<Individual>(root);
			StringWithCustomFacts sex = parent.getData().getSex(); 
			ResultSet rsSpouse = null;
			System.err.println(sex.toString());
			if ("M".equals(sex.toString())) {
				stmtGetWifes.setString(1, personID);
				rsSpouse = stmtGetWifes.executeQuery();
			} else if ("F".equals(sex.toString())) {
				stmtGetHusbands.setString(1, personID);
				rsSpouse = stmtGetHusbands.executeQuery();
			} else {
				System.err.println("Unknown Sex: "+sex);
				return root;
			}
			while (rsSpouse.next()) {
				TreeNode<Individual> spouse = new TreeNode<Individual>(createIndi(rsRoot), parent);
				parent.getChildren().add(spouse);				
				
				String familyID = rsSpouse.getString("f.FamilyID");
				stmtGetChildren.setString(1, familyID);
				ResultSet rsChildren = stmtGetChildren.executeQuery();
				while (rsChildren.next()) {
				}
				rsChildren.close();
			}
			rsSpouse.close();		
		} else { 
			//				System.out.println("Person "+id+" not found!");
		}
		if (rsRoot!= null & !rsRoot.isClosed()) { rsRoot .close(); }
		return root;
	}	

	private Individual createIndi(ResultSet rs) throws SQLException {
		Individual result = new Individual();
		result.setSex(rs.getString("sex"));
		result.setRecIdNumber(rs.getString("personid"));
		result.getNames(true).add(createPersonalName(rs.getString("firstname"), rs.getString("lastname"), rs.getString("nickname")));
		System.out.println(result.getNames());
		System.out.println(result.getSex());
		List<IndividualEvent> events = result.getEvents(true);
		events.add(createEvent(IndividualEventType.BIRTH, rs.getString("birthdate"), rs.getString("birthplace")));
		//events.add(createEvent(IndividualEventType.BAPTISM, rs.getString("birthdate"), rs.getString("birthplace")));
		events.add(createEvent(IndividualEventType.DEATH, rs.getString("deathdate"), rs.getString("deathplace")));
//		events.add(createEvent(IndividualEventType.BURIAL, rs.getString("birthdate"), rs.getString("birthplace")));
		System.out.println(events);
		return result;
	}

	private IndividualEvent createEvent(IndividualEventType type, String date, String placename) {
		IndividualEvent event = new IndividualEvent();
		event.setType(type);
		event.setDate(date);
		Place place = new Place();
		place.setPlaceName(placename);
		event.setPlace(place);
		return event;
	}
	
	private PersonalName createPersonalName(String givenName, String surName, String nickName) {
		PersonalName result = new PersonalName();
		result.setGivenName(givenName);
		result.setSurname(surName);
		if (!nickName.equals("")) {
			result.setNickname(nickName);
		}
		return result;		
	}

	public void printTreeFrom2(String personID, String indent) throws SQLException {
		
		Queue<String> queue = new ArrayBlockingQueue<>(100);
		queue.add(personID);
		
		while (!queue.isEmpty()) {
			String id = queue.poll();
			stmtGetPerson.setString(1, id);
			ResultSet rs = stmtGetPerson.executeQuery();			
			if (rs.next()) {
				printPerson(rs, indent);
				String sex = rs.getString("sex"); 
				rs.close();
				ResultSet rsSpouse = null;
				if (sex.equals("M")) {
					stmtGetWifes.setString(1, personID);
					rsSpouse = stmtGetWifes.executeQuery();
				} else if (rs.getString("sex").equals("F")) {
					stmtGetHusbands.setString(1, personID);
					rsSpouse = stmtGetHusbands.executeQuery();
				}
				while (rsSpouse.next()) {
					printMarriage(rsSpouse, indent);
					printPerson(rsSpouse, indent);
					String familyID = rsSpouse.getString("f.FamilyID");
					stmtGetChildren.setString(1, familyID);
					ResultSet rsChildren = stmtGetChildren.executeQuery();
					while (rsChildren.next()) {
	//					printPerson(rsChildren, "  ");
						System.out.println("enqueue: " + rsChildren.getString("PersonID"));
						queue.add(rsChildren.getString("PersonID"));
					}
					rsChildren.close();
				}
				rsSpouse.close();		
			} else { 
				System.out.println("Person "+id+" not found!");
			}
			if (rs!= null & !rs.isClosed()) { rs .close(); }
		}
	}		
	
	private void printMarriage(ResultSet rs, String indent) throws SQLException {
		System.out.println(indent + "oo\t" + rs.getString("marrdate")+ " in " +rs.getString("marrplace"));		
	}

	private void printPerson(ResultSet rs, String indent) throws SQLException {

		StringBuffer text = new StringBuffer();
		text.append(rs.getString("personid"));
		text.append(indent);
		text.append(rs.getString("lastname"));
		text.append(", ");
		text.append(rs.getString("firstname"));
		if (!rs.getString("nickname").equals("")) {
			text.append(" (");
			text.append(rs.getString("nickname"));
			text.append(")");
		}
		text.append('\t');
		text.append(rs.getString("sex"));
		
		text.append("\t*");		
		text.append(rs.getString("birthdate"));
		if (!rs.getString("birthplace").equals("")) {
			text.append(" in ");		
			text.append(rs.getString("birthplace"));
		}

		text.append("\t+");		
		text.append(rs.getString("deathdate"));
		if (!rs.getString("deathplace").equals("")) {
			text.append(" in ");		
			text.append(rs.getString("deathplace"));
		}

		System.out.println(text);	
	}

	@Override
	public void close() {
		try {
			conn.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}	
	
	public static void main(String[] args) {

		Connection conn = null;
		try {
			Class.forName("com.mysql.cj.jdbc.Driver").newInstance();
		    conn = DriverManager.getConnection("jdbc:mysql://localhost/tng", "tng", "KUmrp7N8NupSpnKz");

		    FamilyTreeBuilder ftb = new FamilyTreeBuilder(conn, "Familie");
		    try {		    	
		    	ftb.importFromTng("I81");
		    	ftb.printTreeFrom2("I81", "");		        
		    } finally {
		    	ftb.close();
		    }	
		} catch (SQLException e) {
		    System.out.println("SQLException: " + e.getMessage());
		    System.out.println("SQLState:     " + e.getSQLState());
		    System.out.println("VendorError:  " + e.getErrorCode());
		} catch (Throwable e) {
			e.printStackTrace();
		}		
	}

}
