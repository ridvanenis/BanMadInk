package org.camunda.hackdays2016.BanMadInkProcesses;

import java.net.InetAddress;
import java.util.List;
import java.util.logging.Logger;

import org.camunda.bpm.engine.HistoryService;
import org.camunda.bpm.engine.delegate.DelegateExecution;
import org.camunda.bpm.engine.delegate.JavaDelegate;
import org.camunda.bpm.engine.history.HistoricDecisionInputInstance;
import org.camunda.bpm.engine.history.HistoricDecisionInstance;
import org.camunda.bpm.engine.history.HistoricDecisionOutputInstance;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

import com.fasterxml.jackson.databind.ObjectMapper;

public class LoadToElasticSearch implements JavaDelegate {
	
	protected static final Logger LOGGER = Logger.getLogger(HistoricDecisionListener.class.getName());
	@Override
	public void execute(DelegateExecution execution) throws Exception {
		
		
		

			HistoryService historyService = execution.getProcessEngineServices().getHistoryService();
			

			
			List<HistoricDecisionInstance> historicDecisionInstances = historyService.createHistoricDecisionInstanceQuery()
					.includeInputs()
					.includeOutputs()
					.decisionDefinitionKey("fraudRating")
					.list();

			
		
			Client client = TransportClient.builder().build()
			        .addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("localhost"), 9300));

			
			for (HistoricDecisionInstance historicDecisionInstance : historicDecisionInstances) {
				//Fill a new object with stuff... 
			FraudScoreTableObject fraudData = new FraudScoreTableObject();
			fraudData.setFraudScore(0);

			fraudData.setFraudInstanceID(historicDecisionInstance.getId());
			
			List<HistoricDecisionInputInstance> inputs = historicDecisionInstance.getInputs();
			for (HistoricDecisionInputInstance historicDecisionInputInstance : inputs) {
				String inputName = historicDecisionInputInstance.getClauseName();
				if(inputName.equals("Payment Has Been Rejected In the Past")){
					fraudData.setPaymentRejected((Boolean)historicDecisionInputInstance.getValue());
				}
				else if(inputName.equals("Number of Past Payouts")){
					fraudData.setNumberOfPayouts((Integer)historicDecisionInputInstance.getValue());
				}
				else if(inputName.equals("History of Fraud")){
					fraudData.setHistoryOfFraud((Boolean)historicDecisionInputInstance.getValue());
				}
				else if(inputName.equals("Payout Amount")){
					fraudData.setCalimAmount((Long)historicDecisionInputInstance.getValue());			}

				
			}
			List<HistoricDecisionOutputInstance> outputs = historicDecisionInstance.getOutputs();
			for (HistoricDecisionOutputInstance historicDecisionOutputInstance : outputs) {
				
				Integer fraudScore = (Integer) historicDecisionOutputInstance.getValue();

				fraudData.setFraudScore(fraudData.getFraudScore()+fraudScore);
				
			}		
			
			ObjectMapper mapper = new ObjectMapper();

			 String serializedHistoricDecisionInstance = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(fraudData);
				
			LOGGER.info(serializedHistoricDecisionInstance);
			

			IndexResponse response = client.prepareIndex("camunda", "fraudData", historicDecisionInstance.getId())
			        .setSource(serializedHistoricDecisionInstance)
			        .get();
			
			LOGGER.info(response.getId());
			
		}

	}
}
