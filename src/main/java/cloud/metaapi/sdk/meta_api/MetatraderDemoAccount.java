package cloud.metaapi.sdk.meta_api;

import cloud.metaapi.sdk.clients.meta_api.models.MetatraderDemoAccountDto;

/**
 * Implements a MetaTrader demo account entity
 */
public class MetatraderDemoAccount {
    
    private MetatraderDemoAccountDto data;
    
    /**
     * Constructs a MetaTrader demo account entity
     * @param data MetaTrader demo account data
     */
   public MetatraderDemoAccount(MetatraderDemoAccountDto data) {
       this.data = data;
   }
   
   /**
    * Returns account login
    * @return account login
    */
   public String getLogin() {
       return data.login;
   }
   
   /**
    * Returns account password
    * @return account password
    */
   public String getPassword() {
       return data.password;
   }
   
   /**
    * Returns account server name
    * @return account server name
    */
   public String getServerName() {
       return data.serverName;
   }
}