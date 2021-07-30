package core.monitoring;

import edu.mit.csail.sdg.alloy4.Err;
import declare.DeclareParserException;
import core.Global;
import core.alloy.codegen.NameEncoder;
import core.alloy.codegen.NameEncoder.DataMappingElement;
import core.exceptions.GenerationException;
import declare.DeclareModel;
import declare.lang.Statement;

import org.deckfour.xes.extension.std.XConceptExtension;
import org.deckfour.xes.model.XEvent;
import org.deckfour.xes.model.XTrace;
import org.deckfour.xes.model.impl.XAttributeMapImpl;
import org.deckfour.xes.model.impl.XTraceImpl;
import org.processmining.operationalsupport.xml.OSXMLConverter;

import declare.DeclareParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MonitorRunner {
	String trace;
    XTraceImpl oneTrace = new XTraceImpl(new XAttributeMapImpl());
    DeclareModel model = new DeclareModel();
    
    boolean conflict;
    OSXMLConverter osxmlConverter = new OSXMLConverter();
    ConstraintChecker constraintChecker = new ConstraintChecker();
    int overallNumberOfConstraints = 0;
    public Map<String, Long> times = new HashMap<>();
    int r = 2;
    NameEncoder encoder;

    public MonitorRunner(boolean conflict) {
        this.conflict = conflict;
    }

    public void setModel(String stringModel) throws DeclareParserException {
        String modelWithDummyStart = "activity complete\n" + stringModel;
        
        DeclareParser modelParser = new DeclareParser();
        
        encoder = new NameEncoder(modelParser);
        if (Global.encodeNames)
        	modelWithDummyStart = encoder.encode(modelWithDummyStart);
        
        model = modelParser.parse(modelWithDummyStart);
        
        // We setModel in the constraintChecker
        constraintChecker.setModel(model);
        //constraintChecker.setEncodings(encoder);
        constraintChecker.setConstraints(model.getConstraints());
        constraintChecker.setDataConstraints(model.getDataConstraints());
        constraintChecker.setAllConstraintArr();
        List<Statement> dataConstraintsCode = modelParser.getDataConstraintsCode();
        
        String[] dataConstraintNameCode = dataConstraintsCode.stream()
										    				.map(Statement::getCode)
										    				.toArray(String[]::new);
        
        constraintChecker.setDataConstraintsName(dataConstraintNameCode);
        constraintChecker.setConstraintStringNames();
        overallNumberOfConstraints = model.getDataConstraints().size() + model.getConstraints().size();
    }

    public String setTrace(String stringTrace) throws DeclareParserException, GenerationException, Err {
        XTrace t = (XTrace) osxmlConverter.fromXML(stringTrace);
        t = encoder.encodeTrace(t);
        
        try {
        	XEvent e = t.get(0);
        	oneTrace.add(e);
        	/*
        	List<String> names = oneTrace.stream()
        			.map(evt -> encoder.getActivityMapping().get( XConceptExtension.instance().extractName(evt) ))
        			.collect(Collectors.toList());
        	System.out.println("Checking constraints over the following (sub)trace:\n" + String.join(", ", names) + "\n");
        	*/
            constraintChecker.setTrace(oneTrace);
            if (oneTrace.size() == 1)
                constraintChecker.initMatrix();
            
            String answer;
            
            String encodedDummyEvent = encoder.getActivityMapping().entrySet().stream()
					.filter(entry -> entry.getValue().equals("complete"))
					.map(Map.Entry::getKey)
					.findFirst().get();
            
            if (XConceptExtension.instance().extractName(e).equals(encodedDummyEvent)) {
            //if (XConceptExtension.instance().extractName(e).equals("complete")) {
                oneTrace.add(e);
                constraintChecker.setTrace(oneTrace);
                if (conflict)
                    constraintChecker.run();
                
                constraintChecker.setFinal();
                answer = constraintChecker.updatedString();
                oneTrace = new XTraceImpl(new XAttributeMapImpl());
                
                // Restart things
                constraintChecker.setConflictedConstraints(new ArrayList<>());
                //constraintChecker.setPermViolatedCon(new ArrayList<>());
                /*
                for (String name : times.keySet()) {
                    String key = name.toString();
                    String value = times.get(name).toString();
                    System.out.println(key + " " + value);
                }
            	*/
            } else {
                //Instant start = Instant.now();
                constraintChecker.run();
                //Instant finish = Instant.now();
                //long timeElapsed = Duration.between(start, finish).toMillis();
                //times.put((e.getAttributes().get("concept:name").toString() + iterator), timeElapsed);
                //iterator++;
                
                if (conflict) {
                    boolean subListCheck = constraintChecker.checkFullConjuction(); // here we find out whether we need to do any further investigation. kui see on false
                    boolean inWhile = !subListCheck; // siis see on true, ja kui eelnev on true, siis see on false ja see on ka see, mida me tahame, sest eelmine konjuktsioon on ok.
                    
                    while (inWhile && ((r <= (overallNumberOfConstraints)) || overallNumberOfConstraints == 2)) { // mis t�hendab, et siinolemine on ull timm
                        inWhile = constraintChecker.checkSublistConjunction(overallNumberOfConstraints, r);
                        
                        if (overallNumberOfConstraints == 2)
                            break;
                        
                        r++;
                    }
                    
                    r = 2; // should be two again, so that we would check the value again afterwards
                }
                
                answer = constraintChecker.updatedString();
            }
            
            // Restoring real (decoded) names 
            for (Map.Entry<String,String> entry : encoder.getActivityMapping().entrySet())
            	answer = answer.replace(entry.getKey(), entry.getValue());
            
            for (DataMappingElement d : encoder.getDataMapping()) {
            	answer = answer.replace(d.getEncodedName(), d.getOriginalName());
            	
            	for (Map.Entry<String,String> entry : d.getValuesMapping().entrySet())
            		answer = answer.replace(entry.getKey(), entry.getValue());
            }
            
            return answer;
            
        } catch (Throwable err) {
            err.printStackTrace();
            return "nothing";
        }
    }
}
