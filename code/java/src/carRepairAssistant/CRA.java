package carRepairAssistant;

import jess.Rete;
import jess.JessException;
import jess.WorkingMemoryMarker;
import java.io.Console;
import java.util.List;
import java.util.ArrayList;

public class CRA {
    private final static int    COMPONENT =0,
                                STATE =1,
                                COMPLAINT = 0,
                                NAME =1;

    private Rete jess;
    private ConsoleCheat c;
    private String[] currentHypothesis = {"", ""};

    private void askComplaint() {
        c.printf("Your complaint is?\n");
        String observable = c.readLine();
        String fact = "(complaint " + observable + " TRUE)";
        try {
            jess.assertString(fact);
        } catch (JessException ex) {
            System.err.println(ex);
        }
    }

    private void askLikelyComplaint() throws JessException{
        String observable, choice;
        List<String[]> allComplaints = allComplaints();
        //Print
        c.printf("Likely complaints are:\n");
        for(int i=0; i<allComplaints.size(); i++){
            c.printf(i + ": " + allComplaints.get(i)[NAME] + " (id: " + allComplaints.get(i)[COMPLAINT] + ")\n");
        }
        c.printf("Your complaint is?\n");

        //Receive input
        choice = c.readLine();
        choice = choice.trim();
        if (isNumber(choice)){
            int nrChoice = Integer.parseInt(choice);
            observable = allComplaints.get(nrChoice)[COMPLAINT];
        } else {
            observable = choice;
        }

        //Assert complaint
        String fact = "(complaint " + observable + " TRUE)";
        try {
            jess.assertString(fact);
        } catch (JessException ex) {
            System.err.println(ex);
        }
    }

    private void askAvailableHypothesis(List<String[]> allHypothesis) throws JessException{
        //Print
        c.printf("Available hypothesis are:\n");
        for(int i=0; i<allHypothesis.size(); i++){
            c.printf(i + " " + allHypothesis.get(i)[COMPONENT] + " is " + allHypothesis.get(i)[STATE] + "\n");
        }
		c.printf("We suggest: " + currentHypothesis[COMPONENT] + " is " + currentHypothesis[STATE] + "\n");
        c.printf("Do you have an other suggestion (no/nr/id)?\n");

		//Receive input
        String choice = c.readLine();
        choice = choice.trim();
        if (isNumber(choice)){
            int nrChoice = Integer.parseInt(choice);
            currentHypothesis = allHypothesis.remove(nrChoice);
        } else if(choice.contains(" is ")) {
            currentHypothesis[COMPONENT] = choice.substring(0, choice.indexOf(" "));
            currentHypothesis[STATE] = choice.substring(choice.lastIndexOf(" ")+1, choice.length());
        } else {
            currentHypothesis = allHypothesis.remove(allHypothesis.size()-1);
        }
    }

    private boolean isNumber(String string){
        try{
            Integer.parseInt(string);
            return true;
        }
        catch(NumberFormatException nfe){
            return false;
        }
    }

    private List<String[]> allHypothesis() throws JessException{
        List<String[]> result = new ArrayList<String[]>();
        jess.QueryResult hypothesis = generateHypothesis();

        while (hypothesis.next()) {
            String[] h = new String[2];
            h[COMPONENT] = hypothesis.getString("component");
            h[STATE] = hypothesis.getString("state");
            result.add(h);
        }
        return result;
    }

    private List<String[]> allComplaints() throws JessException {
        List<String[]> result = new ArrayList<String[]>();
        jess.QueryResult likely_complaints = jess.runQueryStar("search-likely-complaints", new jess.ValueVector());

        while (likely_complaints.next()){
            String[] h = new String[2];
            h[COMPLAINT] = likely_complaints.getString("observable");
            h[NAME] = likely_complaints.getString("name");
            result.add(h);
        }

        return result;
    }

    private int countQueryResult(jess.QueryResult queryResult) throws JessException{
        int result = 0;
        while(queryResult.next()){
            result++;
        }
        return result;
    }

    private jess.QueryResult generateHypothesis() throws JessException {
        return jess.runQueryStar("search-hypothesis", new jess.ValueVector());
    }

    private boolean selectHypothesis(List<String[]> hypothesis) throws JessException {
        if(hypothesis.size() != 0) {
            currentHypothesis = hypothesis.get(0);
            return true;
        } else {
            return false;
        }
    }

    private void printHypothesis() {
        c.printf(
            "The hypothesis is that " +
            currentHypothesis[COMPONENT] +
            " is " + 
            currentHypothesis[STATE] + "\n"
        );
    }

    private void printFacts() {
        try {
            java.io.Writer co = new java.io.PrintWriter(System.out);
            co.write("-----------------------------------\n");
            jess.ppFacts(co);
            co.flush();
        } catch (java.io.IOException ex) {
            System.err.println(ex);
        }
    }

    private boolean negotiateObservable() throws JessException {
		WorkingMemoryMarker beforeHypothesis = jess.mark();
        c.printf("Trying hypothesis\n");
        jess.assertString(
            "(hypothesis " +
            currentHypothesis[COMPONENT] + " " +
            currentHypothesis[STATE] + ")"
        );
        jess.run();
        //printFacts();
        c.printf("Querying Observables\n");
		jess.QueryResult observables =
	    jess.runQueryStar("search-observable", new jess.ValueVector());
        String answer = "no";
		while (observables.next() && answer.equals("no")) {
            String observable = observables.getString("observable");
            c.printf(
                "You want to observe " +
                observable + "? true/false/no\n"
            );
            boolean noAnswer = true;
            while (noAnswer) {
                answer = c.readLine();
                if (answer.equals("true")) {
                    jess.resetToMark(beforeHypothesis);
                    jess.assertString(
                        "(observed " +
                        observable +
                        " TRUE)"
                    );
                    jess.run();
                    return true;
                } else if (answer.equals("false")) {
                    jess.resetToMark(beforeHypothesis);
                    jess.assertString(
                        "(observed " +
                        observable +
                        " FALSE)"
                    );
                    jess.run();
                    return true;
                } else if (answer.equals("no")) {
                    break;
                } else {
                    c.printf("try again: true/false/no\n");
                    continue;
                }
            }
		}
		c.printf("No observables for this hypothesis \n");
        return false;
    }

    public CRA() {
        jess = new Rete();
        c = new ConsoleCheat();
        try {
            jess.batch("jess/test/test-likely-complaints.jess");
            jess.reset();
            printFacts();
            askLikelyComplaint();
            //printFacts();
            jess.run();
            boolean found_hypothesis;
            do {
                List<String[]> hypothesis = allHypothesis();
                found_hypothesis = (hypothesis.size() > 0);
                boolean observed = false;
                while(!observed && hypothesis.size() > 0) {
                    askAvailableHypothesis(hypothesis);
                    printHypothesis();
                    observed = negotiateObservable();
                }
                if (!observed) break;
            } while (found_hypothesis);
        } catch (JessException ex) {
            System.err.println(ex);
        }
    }

    public static void main(String[] arg) {
        new CRA();
    }
}
