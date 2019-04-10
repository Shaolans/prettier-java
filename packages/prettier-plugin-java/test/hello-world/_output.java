package eniac.automatichandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jfree.ui.RefineryUtilities;
import eniac.automatichandler.connectors.AutomaticHandlerRequestConnector;
import eniac.automatichandler.interfaces.AutomaticHandlerManagementI;
import eniac.automatichandler.interfaces.AutomaticHandlerRequestI;
import eniac.automatichandler.ports.AutomaticHandlerManagementInboundPort;
import eniac.automatichandler.ports.AutomaticHandlerRequestOutboundPort;
import eniac.processorcoordinator.connectors.ProcessorCoordinatorFreqConnector;
import eniac.processorcoordinator.interfaces.ProcessorCoordinatorOrderI;
import eniac.processorcoordinator.ports.ProcessorCoordinatorFreqOutboundPort;
import eniac.processorcoordinator.ports.ProcessorCoordinatorOrderInboundPort;
import eniac.requestdispatcher.connectors.RequestDispatcherManagementConnector;
import eniac.requestdispatcher.interfaces.RequestDispatcherDynamicStateI;
import eniac.requestdispatcher.interfaces.RequestDispatcherStateDataConsumerI;
import eniac.requestdispatcher.interfaces.RequestDispatcherStaticStateI;
import eniac.requestdispatcher.ports.RequestDispatcherDynamicStateDataOutboundPort;
import eniac.requestdispatcher.ports.RequestDispatcherManagementOutboundPort;
import eniac.requestdispatcher.ports.RequestDispatcherStaticStateDataOutboundPort;
import fr.sorbonne_u.components.AbstractComponent;
import fr.sorbonne_u.components.connectors.DataConnector;
import fr.sorbonne_u.components.exceptions.ComponentShutdownException;
import fr.sorbonne_u.datacenter.connectors.ControlledDataConnector;
import fr.sorbonne_u.datacenter.hardware.processors.UnacceptableFrequencyException;
import fr.sorbonne_u.datacenter.hardware.processors.UnavailableFrequencyException;
import fr.sorbonne_u.datacenter.software.applicationvm.interfaces.ApplicationVMDynamicStateI;
import fr.sorbonne_u.datacenter.software.applicationvm.interfaces.ApplicationVMStaticStateI;

/**
*Le gestionnaire automatique demande la modification de facteurs au controleur d'admission
*ou au coordinateur de fréquence en fonction des données du répartiteur de requête
*qu'il reçoit toutes les 500 ms.
*
*/
public class AutomaticHandler extends AbstractComponent implements RequestDispatcherStateDataConsumerI, ProcessorCoordinatorOrderI {
    /**
    * URI de l'AutomaticHandler
    */
    protected String autoHand_uri;
    /**
    * Port pour recevoir dynamique des données du répartiteur de requêtes
    */
    protected RequestDispatcherDynamicStateDataOutboundPort requestDispatcherDynamicStateDataOutboundPort;
    /**
    * Port pour recevoir les donnéesd statiques du répartiteur de requêtes
    */
    protected RequestDispatcherStaticStateDataOutboundPort requestDispatcherStaticStateDataOutboundPort;
    /**
    * Port pour envoyer des demandes à l'AdmissionController
    */
    protected AutomaticHandlerRequestOutboundPort automaticHandlerRequestOutboundPort;
    /**
    * URI de automaticHandlerRequest inbound port sur lequel il faut se connecter
    */
    protected String automaticHandlerRequestInboundPortURI;
    /**
    * URI de requestDispatcherDynamic inbound port sur lequel il faut se connecter
    */
    protected String requestDispatcherDynamicStateDataInboundPortURI;
    /**
    * URI de requestDispatcherStatic inbound port sur lequel il faut se connecter
    */
    protected String requestDispatcherStaticStateDataInboundPortURI;
    /**
    * URI du RequestDispatcher
    */
    protected String requestDispatcherURI;
    /**
    * Port de management du RequestDispatcher
    */
    protected RequestDispatcherManagementOutboundPort dispatcher_management_outport;
    /**
    * Dernieres données du RequestDispatcher reçues par le port dynamique
    */
    protected RequestDispatcherDynamicStateI current_ds;
    /**
    * Map des fréquences admissibles par processeurs présents sur le RequestDispatcher
    */
    protected Map<String, Map.<String, Set.<Integer>>> admissibleFreqCores;
    /**
    * Affichage graphique
    */
    private ComputeTimeCharts chart;
    /**
    * borne inferieure pour la moyenne
    */
    private double lower_bound;
    /**
    * borne supérieure pour la moyenne
    */
    private double upper_bound;
    /**
    * dernière moyenne recue
    */
    private double last;
    /**
    * Taille maximal des queues de requetes des avms
    */
    public static final int MAX_QUEUE = 3;
    /**
    *
    */
    private double lavg;
    /**
    * Map Processor URI / ProcessorCoordinatorFreqOutboundPort
    */
    protected Map<String, ProcessorCoordinatorFreqOutboundPort> proc_coord_freq_map;
    /**
    * Map qui contient l'URI du ProcessorCoordinatorOrderInboundPort par Processor URI
    */
    protected Map<String, String> proc_coord_order_map;
    /**
    * Map qui contient l'URI du processorCoordinatorFreqInportURIS par Processor URI
    */
    protected Map<String, String> processorCoordinatorFreqInportURIS;
    /**
    * URI du port de management du RequestDispatcher
    */
    protected String requestDispatcherManagementInboundPortURI;
    /**
    * Temps moyen de réponse
    */
    protected double averageResponseTime;
    /**
    * Modulation de prise de décisions par l'AutomaticHandler
    */
    protected int modWait = 20;
    /**
    * Indice de modulation
    */
    private int wait = 15;
    /**
    * Booléen indiquant si on a déjà demandé de stop l'envoie de requetes à
    * une AVM
    */
    private boolean avmWaitingToRemove;

    /**
    * Contructeur de l'AutomaticHandler
    * @param autoHand_uri	URI de l'AutomaticHandler
    * @param managementInboundPortURI	URI du port de management de l'AutomaticHandler
    * @param requestDispatcherUri	URI du RequestDispatcher
    * @param requestDispatcherManagementInboundPortURI	URI du port de management du RequestDispatcher
    * @param automaticHandlerRequestInboundPortURI URI de automaticHandlerRequestInboundPortURI
    * @param requestDispatcherDynamicStateDataInboundPortURI URI de requestDispatcherDynamicStateDataInboundPort
    * @param requestDispatcherStaticStateDataInboundPortURI URI de requestDispatcherStaticStateDataInboundPort
    * @param averageResponseTime	temps moyen de réponse
    * @param processorCoordinatorFreqInportURIS Map des processorCoordinatorFreqInportURIS par Processor URI
    * @throws Exception exception
    */
    public AutomaticHandler(String autoHand_uri, String managementInboundPortURI, String requestDispatcherUri, String requestDispatcherManagementInboundPortURI, String automaticHandlerRequestInboundPortURI, String requestDispatcherDynamicStateDataInboundPortURI, String requestDispatcherStaticStateDataInboundPortURI, Double averageResponseTime, HashMap<String, String> processorCoordinatorFreqInportURIS) throws Exception {
        super(autoHand_uri, 1, 1);
        assert autoHand_uri != null ;
        assert managementInboundPortURI != null ;
        assert automaticHandlerRequestInboundPortURI != null ;
        assert requestDispatcherDynamicStateDataInboundPortURI != null ;
        assert requestDispatcherStaticStateDataInboundPortURI != null ;
        assert requestDispatcherUri != null ;
        assert processorCoordinatorFreqInportURIS != null ;
        assert requestDispatcherManagementInboundPortURI != null ;
        this.requestDispatcherManagementInboundPortURI = requestDispatcherManagementInboundPortURI;
        this.requestDispatcherURI = requestDispatcherUri;
        this.autoHand_uri = autoHand_uri;
        this.requestDispatcherDynamicStateDataInboundPortURI = requestDispatcherDynamicStateDataInboundPortURI;
        this.requestDispatcherStaticStateDataInboundPortURI = requestDispatcherStaticStateDataInboundPortURI;
        this.automaticHandlerRequestInboundPortURI = automaticHandlerRequestInboundPortURI;
        addOfferedInterface(AutomaticHandlerManagementI.class);
        AutomaticHandlerManagementInboundPort automaticHandlerManagementInboundPort = new AutomaticHandlerManagementInboundPort(autoHand_uri, this);
        addPort(automaticHandlerManagementInboundPort);
        automaticHandlerManagementInboundPort.publishPort();
        addRequiredInterface(AutomaticHandlerRequestI.class);
        automaticHandlerRequestOutboundPort = new AutomaticHandlerRequestOutboundPort(this);
        addPort(automaticHandlerRequestOutboundPort);
        automaticHandlerRequestOutboundPort.publishPort();
        addRequiredInterface(RequestDispatcherDynamicStateI.class);
        requestDispatcherDynamicStateDataOutboundPort = new RequestDispatcherDynamicStateDataOutboundPort(this, requestDispatcherUri);
        addPort(requestDispatcherDynamicStateDataOutboundPort);
        requestDispatcherDynamicStateDataOutboundPort.publishPort();
        addRequiredInterface(RequestDispatcherStaticStateI.class);
        requestDispatcherStaticStateDataOutboundPort = new RequestDispatcherStaticStateDataOutboundPort(this, requestDispatcherUri);
        addPort(requestDispatcherStaticStateDataOutboundPort);
        requestDispatcherStaticStateDataOutboundPort.publishPort();
        toggleLogging();
        toggleTracing();
        chart = new ComputeTimeCharts(autoHand_uri, averageResponseTime);
        chart.pack();
        RefineryUtilities.positionFrameRandomly(chart);
        chart.setVisible(true);
        lower_bound = averageResponseTime - 500;
        upper_bound = averageResponseTime + 500;
        proc_coord_order_map = new HashMap<>();
        proc_coord_freq_map = new HashMap<>();
        for (String proc_uri: processorCoordinatorFreqInportURIS.keySet()) {
            ProcessorCoordinatorFreqOutboundPort outport = new ProcessorCoordinatorFreqOutboundPort(this);
            this.addPort(outport);
            outport.publishPort();
            String inport_uri = autoHand_uri + "_proc_order_inport";
            ProcessorCoordinatorOrderInboundPort inport = new ProcessorCoordinatorOrderInboundPort(inport_uri, this);
            this.addPort(inport);
            inport.publishPort();
            proc_coord_freq_map.put(proc_uri, outport);
            proc_coord_order_map.put(proc_uri, inport_uri);
        }
        this.processorCoordinatorFreqInportURIS = processorCoordinatorFreqInportURIS;
        this.averageResponseTime = averageResponseTime;
        dispatcher_management_outport = new RequestDispatcherManagementOutboundPort(this);
        addPort(dispatcher_management_outport);
        dispatcher_management_outport.publishPort();
    }

    @Override public void finalise() throws Exception {
        automaticHandlerRequestOutboundPort.doDisconnection();
        requestDispatcherDynamicStateDataOutboundPort.doDisconnection();
        requestDispatcherStaticStateDataOutboundPort.doDisconnection();
        for (String uri: proc_coord_freq_map.keySet()) {
            ProcessorCoordinatorFreqOutboundPort port = proc_coord_freq_map.get(uri);
            if (port.connected()) port.doDisconnection();
        }
        super.finalise();
    }

    @Override public void shutdown() throws ComponentShutdownException {
        try  {
            automaticHandlerRequestOutboundPort.unpublishPort();
            requestDispatcherDynamicStateDataOutboundPort.unpublishPort();
            requestDispatcherStaticStateDataOutboundPort.unpublishPort();
            for (String uri: proc_coord_freq_map.keySet()) {
                ProcessorCoordinatorFreqOutboundPort port = proc_coord_freq_map.get(uri);
                port.unpublishPort();
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        super.shutdown();
    }

    @Override public void start() {
        try  {
            doPortConnection(automaticHandlerRequestOutboundPort.getPortURI(), automaticHandlerRequestInboundPortURI, AutomaticHandlerRequestConnector.class.getCanonicalName());
            doPortConnection(requestDispatcherDynamicStateDataOutboundPort.getPortURI(), requestDispatcherDynamicStateDataInboundPortURI, ControlledDataConnector.class.getCanonicalName());
            doPortConnection(requestDispatcherStaticStateDataOutboundPort.getPortURI(), requestDispatcherStaticStateDataInboundPortURI, DataConnector.class.getCanonicalName());
            for (String proc_uri: proc_coord_freq_map.keySet()) {
                ProcessorCoordinatorFreqOutboundPort outport = proc_coord_freq_map.get(proc_uri);
                doPortConnection(outport.getPortURI(), processorCoordinatorFreqInportURIS.get(proc_uri), ProcessorCoordinatorFreqConnector.class.getCanonicalName());
                outport.addProcessorCoordinatorOrderOutboundPort(autoHand_uri, proc_coord_order_map.get(proc_uri));
            }
            doPortConnection(dispatcher_management_outport.getPortURI(), requestDispatcherManagementInboundPortURI, RequestDispatcherManagementConnector.class.getCanonicalName());
            requestDispatcherDynamicStateDataOutboundPort.startUnlimitedPushing(500);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Override public void acceptRequestDispatcherStaticData(String requestDisptacherURI, RequestDispatcherStaticStateI staticState) throws Exception {
        Map<String, ApplicationVMStaticStateI> avmStaticStateMap = staticState.getAVMStaticStateMap();
        admissibleFreqCores = new HashMap<>();
        for (String avmUri: avmStaticStateMap.keySet()) {
            ApplicationVMStaticStateI avmStaticState = avmStaticStateMap.get(avmUri);
            Map<Integer, Integer> coreMap = avmStaticState.getIdCores();
            for (Integer core: coreMap.keySet()) {
                logMessage(avmUri + " : core number " + core + " ; processor number " + String.valueOf(coreMap.get(core)));
                admissibleFreqCores.put(avmUri, avmStaticState.getAdmissibleFreqCores());
            }
        }
    }

    /**
    * Augmentation de la fréquence des coeurs des AVM
    * @param avmdynamicstate	Données dynamiques des AVM
    * @param avm	URI de l'AVM
    * @return true si la fréquence d'un coeur d'une AVM a pu être augmentée, false sinon
    */
    private boolean increaseSpeed(Map<String, ApplicationVMDynamicStateI> avmdynamicstate, String avm) {
        try  {
            ApplicationVMDynamicStateI avmDynamicState = avmdynamicstate.get(avm);
            Map<String, Set.<Integer>> admissibleFreqCoresAVM = admissibleFreqCores.get(avm);
            for (String proc_uri: avmDynamicState.getProcCurrentFreqCoresMap().keySet()) {
                Set<Integer> admissibleFreq = admissibleFreqCoresAVM.get(proc_uri);
                for (int core: avmDynamicState.getProcCurrentFreqCoresMap().get(proc_uri).keySet()) {
                    int currentFreq = avmDynamicState.getProcCurrentFreqCoresMap().get(proc_uri).get(core);
                    int freq = getNextFreq(currentFreq, admissibleFreq);
                    if (currentFreq == freq) continue;
                    return (proc_coord_freq_map.get(proc_uri).setCoreFrequency(autoHand_uri, core, freq));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
    * Diminution de la fréquence des coeurs des AVM
    * @param avmdynamicstate	Données dynamiques des AVM
    * @param avm	URI de l'AVM
    * @return true si la fréquence d'un coeur d'une AVM a pu être diminuée, false sinon
    */
    private boolean decreaseSpeed(Map<String, ApplicationVMDynamicStateI> avmdynamicstate, String avm) {
        ApplicationVMDynamicStateI avmDynamicState = avmdynamicstate.get(avm);
        Map<String, Set.<Integer>> admissibleFreqCoresAVM = admissibleFreqCores.get(avm);
        for (String proc_uri: avmDynamicState.getProcCurrentFreqCoresMap().keySet()) {
            Set<Integer> admissibleFreq = admissibleFreqCoresAVM.get(proc_uri);
            for (int core: avmDynamicState.getProcCurrentFreqCoresMap().get(proc_uri).keySet()) {
                int currentFreq = avmDynamicState.getProcCurrentFreqCoresMap().get(proc_uri).get(core);
                int freq = getPreviousFreq(currentFreq, admissibleFreq);
                if (currentFreq == freq) continue;
                try  {
                    if (proc_coord_freq_map.get(proc_uri).setCoreFrequency(autoHand_uri, core, freq)) return true;
                } catch (UnavailableFrequencyException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (UnacceptableFrequencyException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    @Override public void acceptRequestDispatcherDynamicData(String requestDisptacherURI, RequestDispatcherDynamicStateI dynamicState) throws Exception {
        lavg = dynamicState.getAverageRequestTime();
        chart.addData(lavg);
        if (wait % modWait == ) {
            logMessage("Modulation possible");
            modulateAVM(dynamicState, lavg);
        }
        wait++;
        current_ds = dynamicState;
    }

    /**
    * Statégie de modulation
    * Si on est au dessus de la borne supérieure on va commencer par essayer d'augmenter la fréquence.
    * Si ca n'a pas été fait, on va ajouter deux cores par avm si on est au dessus de 2 fois la moyenne, sinon un core.
    * Si ca n'a pas été fait, on va ajouter une avm.
    * Si on est dans la borne, on ne fait rien.
    * Si on est en dessus de la borne, on va commencer par enlever les AVM qui n'ont pas de requêtes,
    * sinon, on va enlever un coeur à chaque avm, sinon on va dimunuer la fréquence des coeurs.
    * @param dynamicstate données dynamique du RequestDispatcher
    * @param avg la moyenne
    * @throws Exception exception
    */
    public void modulateAVM(RequestDispatcherDynamicStateI dynamicstate, double avg) throws Exception {
        Map<String, String> proc_coord_freq_inport_uri_map;
        if (avg > upper_bound) {
            if (last > avg) {
                last = avg;
            } else {
                logMessage("Response time too long: " + avg + "ms (<" + upper_bound + " ms wanted)");
                for (Map.Entry<String, Double> entry: dynamicstate.getScoresMap().entrySet()) {
                    if (entry.getValue() > MAX_QUEUE) {
                        if (increaseSpeed(dynamicstate.getAVMDynamicStateMap(), entry.getKey())) {
                            logMessage(entry.getKey() + " frequency increased");
                        } else {
                            int nbCoreToAdd;
                            if (lavg > 2 * averageResponseTime) {
                                nbCoreToAdd = 2;
                            } else nbCoreToAdd = 1;
                            if ((proc_coord_freq_inport_uri_map = automaticHandlerRequestOutboundPort.addCoreToAvm(autoHand_uri, entry.getKey(), nbCoreToAdd)) != null) {
                                addNewPortCoord(proc_coord_freq_inport_uri_map);
                                logMessage(entry.getKey() + " 1 core added");
                                wait = 10;
                            } else {
                                if (getUnusedAVMs(dynamicstate).size() ==  && (proc_coord_freq_inport_uri_map = automaticHandlerRequestOutboundPort.addAVMToRequestDispatcher(autoHand_uri, requestDispatcherURI)) != null) {
                                    addNewPortCoord(proc_coord_freq_inport_uri_map);
                                    logMessage("avm added");
                                    wait = 10;
                                }
                            }
                        }
                    } else {
                        if (getUnusedAVMs(dynamicstate).size() ==  && (proc_coord_freq_inport_uri_map = automaticHandlerRequestOutboundPort.addAVMToRequestDispatcher(autoHand_uri, requestDispatcherURI)) != null) {
                            addNewPortCoord(proc_coord_freq_inport_uri_map);
                            logMessage("avm added");
                            wait = 10;
                        }
                    }
                }
            }
        } else {
            if (avg < lower_bound) {
                if (last < avg) {
                    last = avg;
                } else {
                    logMessage("Response time too fast: " + avg + "ms (>" + lower_bound + " ms wanted)");
                    if (removeUnusedAVM(dynamicstate)) {
                        wait = 15;
                        return;
                    }
                    boolean ok = false;
                    for (String avm: dynamicstate.getAVMDynamicStateMap().keySet()) if (automaticHandlerRequestOutboundPort.removeCoreFromAvm(autoHand_uri, avm) != null) {
                        logMessage(avm + " removed 1 core");
                        wait = 15;
                        ok = true;
                    }
                    if (!ok) {
                        for (String avmUri: dynamicstate.getAVMDynamicStateMap().keySet()) if (decreaseSpeed(dynamicstate.getAVMDynamicStateMap(), avmUri)) {
                            logMessage(avmUri + " speed decreased");
                        }
                        wait = 18;
                    }
                }
            } else {
                last = avg;
                logMessage("Response time correct");
                wait = 19;
            }
        }
    }

    /**
    * Création des ProcessorCoordinatorFreqOutboundPort et connexion, puis
    * demande au ProcessorCoordinator d'ajouter les ProcessorCoordinatorOrderOutboundPort
    * @param proc_coord_freq_inport_uri_map contient les URIS des inbound port de ProcessorCoordinatorFreq
    */
    private void addNewPortCoord(Map<String, String> proc_coord_freq_inport_uri_map) {
        for (String proc_uri: proc_coord_freq_inport_uri_map.keySet()) {
            try  {
                ProcessorCoordinatorFreqOutboundPort outport;
                outport = new ProcessorCoordinatorFreqOutboundPort(this);
                addPort(outport);
                outport.publishPort();
                doPortConnection(outport.getPortURI(), proc_coord_freq_inport_uri_map.get(proc_uri), ProcessorCoordinatorFreqConnector.class.getCanonicalName());
                proc_coord_freq_map.put(proc_uri, outport);
                String processorCoordinatorOrderInboundPortURI = "proc_coord_order_uri_" + autoHand_uri;
                ProcessorCoordinatorOrderInboundPort inport = new ProcessorCoordinatorOrderInboundPort(processorCoordinatorOrderInboundPortURI, this);
                addPort(inport);
                inport.publishPort();
                proc_coord_order_map.put(proc_uri, processorCoordinatorOrderInboundPortURI);
                outport.addProcessorCoordinatorOrderOutboundPort(autoHand_uri, processorCoordinatorOrderInboundPortURI);
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    /**
    * Enlève les AVM qui n'ont aucune requête en cours de traitement.
    * @param dynamicstate Données dynamiques des AVM
    * @return true s'il y a eut retrait
    */
    private boolean removeUnusedAVM(RequestDispatcherDynamicStateI dynamicstate) {
        List<String> unusedavms = getUnusedAVMs(dynamicstate);
        if (unusedavms.size() <= 1) return false;
        for (String avm: unusedavms) {
            try  {
                if (automaticHandlerRequestOutboundPort.removeAVMFromRequestDispatcher(autoHand_uri, requestDispatcherURI, avm) != null) {
                    logMessage(avm + " removed.");
                    avmWaitingToRemove = false;
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
    *	Donne la liste des AVMS qui n'ont aucune requete en cours de traitemnet.
    *Demande au RequetDispatcher d'arreter d'envoyer des requetes sur une de ses AVM
    *quand cette liste est vide
    * @param dynamicstate Données dynamiques des AVM
    * @return liste des uri des AVMs qui ne sont pas utilisés
    */
    private List<String> getUnusedAVMs(RequestDispatcherDynamicStateI dynamicstate) {
        List<String> avms = new ArrayList<>();
        for (Map.Entry<String, Double> entry: dynamicstate.getScoresMap().entrySet()) {
            if (entry.getValue() == ) {
                avms.add(entry.getKey());
            }
        }
        if (avms.size() ==  && !avmWaitingToRemove) try  {
            dispatcher_management_outport.stopSendingRequestToOneAVM();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } else avmWaitingToRemove = true;
        return avms;
    }

    /**
    * Calcule la fréquence possible qui est plus basse que l'actuelle
    * @param currentFreq	fréquence actuelle
    * @param freqs	ensemble des fréquences possibles
    * @return la fréquence d'haut dessus
    */
    public int getNextFreq(int currentFreq, Set<Integer> freqs) {
        int ret = currentFreq;
        for (Integer i: freqs) {
            if (i > ret) ret = i;
        }
        for (Integer i: freqs) {
            if (i > currentFreq) {
                if (i < ret) {
                    ret = i;
                }
            }
        }
        return ret;
    }

    /**
    * Calcule la fréquence possible qui est plus basse que l'actuelle
    * @param currentFreq	fréquence actuelle
    * @param freqs	ensemble des fréquences possibles
    * @return la fréquence d'en dessous
    */
    public int getPreviousFreq(int currentFreq, Set<Integer> freqs) {
        int ret = currentFreq;
        for (Integer i: freqs) {
            if (i < ret) ret = i;
        }
        for (Integer i: freqs) {
            if (i < currentFreq) {
                if (i > ret) ret = i;
            }
        }
        return ret;
    }

    @Override public void setCoreFreqNextTime(String procURI, int coreNo, int frequency) throws Exception {
        proc_coord_freq_map.get(procURI).setCoreFrequency(autoHand_uri, coreNo, frequency);
    }

    @Override public void removeFreqPort(String procURI) throws Exception {
        this.proc_coord_freq_map.remove(procURI).unpublishPort();
    }
}
