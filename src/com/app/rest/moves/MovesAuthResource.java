package com.app.rest.moves;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.apache.http.client.ClientProtocolException;
import org.apache.log4j.Logger;

import com.app.Constants;
import com.app.entity.SummaryResponse;
import com.app.entity.SummaryResponse.SummaryType;
import com.app.entity.TokenResponse;
import com.app.entity.moves.MovesData;
import com.app.entity.moves.MovesUser;
import com.app.manager.MovesDataManager;
import com.app.manager.MovesUserManager;
import com.app.service.moves.MovesApiService;
import com.app.service.moves.MovesOAuthService;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@Path("/auth/moves")
public class MovesAuthResource {
	
	private final static Logger LOG = Logger.getLogger(MovesAuthResource.class);
	
	private final static String ERROR_SUMMARY_RESPONSE = 
			"Error occured. Could not authenticate with your Moves account";
	
	@Context
    private ServletContext _context;
	private ConcurrentMap<String, Map<String, String>> _contextCache;
	
	@PostConstruct
	@SuppressWarnings("unchecked")
    public void init() {
		_contextCache = (ConcurrentMap) _context.getAttribute(Constants.CACHE_KEY);
    }
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public SummaryResponse authenticateMoves(@QueryParam("user_id") String userId) throws ClientProtocolException, IOException {
		System.out.println("Attempting to authenticate Moves with userId " + userId);
		
		Map<String, String> movesTokenMap = _contextCache.get(userId + "movesTokenMap");
		
		System.out.println("Got movesTokenMap from contextCache for userId " + userId);
		
		if (movesTokenMap == null) {
			return new SummaryResponse(400, null, null,
					ERROR_SUMMARY_RESPONSE, null, 0, SummaryType.ERROR);
		}
		
		MovesOAuthService movesOAuthService = new MovesOAuthService();
		
		String response = "";
		try {
			
			System.out.println("Trying to authorize Moves with 8-digit code entered by user.");
			
			if (!movesOAuthService.postCheckAuthorized(movesTokenMap)) {
				return new SummaryResponse(400, null, null, ERROR_SUMMARY_RESPONSE,
						null, 0, SummaryType.ERROR);
			}
			
			System.out.println("Moves 8-digit code authorize success. Attempt to get accessToken.");
			
			String authCode = movesOAuthService.authorizeAndRedirect(movesTokenMap);
			
			response = movesOAuthService.getAccessToken(authCode, movesTokenMap);
		} catch (Exception e) {
			return new SummaryResponse(400, null, null, 
					ERROR_SUMMARY_RESPONSE, null, 0, SummaryType.ERROR);
		}
		
		System.out.println("Successfully obtained Moves accessToken for userId " + userId);
		
		Gson gson = new GsonBuilder()
			    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
			    .create();
		
		TokenResponse tokenResponse = gson.fromJson(response, TokenResponse.class);
		
		System.out.println("Attempt to save new MovesUser containing necessary Moves tokens");
		
		// save new MovesUser containing necessary Moves tokens
		MovesUserManager movesUserManager = new MovesUserManager();
		MovesUser movesUser = new MovesUser();
		movesUser.setAccessToken(tokenResponse.getAccessToken());
		movesUser.setRefreshToken(tokenResponse.getRefreshToken());
		movesUser.setUserId(userId);
		movesUser.setDataRowCount(0);
		movesUserManager.saveMovesUser(movesUser);
		
		System.out.println("Attempt to retrieve daily co2e for MovesUser with userId " + userId);
		
		MovesApiService apiService = new MovesApiService();
		double co2e = apiService.getDailyCarbon(tokenResponse.getAccessToken());
		
		System.out.println("Daily co2e '" + co2e + "' retrieved for MovesUser with userId " + userId);
		
		MovesData movesData = new MovesData();
		movesData.setCo2E(co2e);
		movesData.setUserId(movesUser.getUserId());
		
		MovesDataManager movesDataManager = new MovesDataManager();
		movesDataManager.saveMovesData(movesData);
		
		System.out.println("Saved MovesData for userId " + userId);
		
		// increment the total data count for the user
		movesUserManager.incrementDataRowCount(movesUser.getUserId());
		
		System.out.println("Incremented data_row_count for MovesUser with userId " + userId);
		
		return new SummaryResponse(200, null, null, 
				"You have now linked your Moves account! Your current co2e is " + co2e, null, 0,
					SummaryType.INFO);
	}
}
