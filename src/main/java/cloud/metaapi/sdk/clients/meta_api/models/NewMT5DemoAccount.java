package cloud.metaapi.sdk.clients.meta_api.models;

/**
 * New MetaTrader 5 demo account model
 */
public class NewMT5DemoAccount {
  /**
   * Account type. Available account type values can be found in mobile MT application or in MT terminal downloaded
   * from our broker
   */
  public String accountType;
  /**
   * Account holder's address, or {@code null}
   */
  public String address;
  /**
   * Account balance
   */
  public double balance;
  /**
   * Account holder's city, or {@code null}
   */
  public String city;
  /**
   * Account holder's country, or {@code null}
   */
  public String country;
  /**
   * Account holder's email
   */
  public String email;
  /**
   * Language id, or {@code null} (default is 1)
   */
  public Integer languageId;
  /**
   * Account leverage
   */
  public double leverage;
  /**
   * Account holder's name, or {@code null}
   */
  public String name;
  /**
   * Account holder's phone, or {@code null}
   */
  public String phone;
  /**
   * Server name
   */
  public String serverName;
  /**
   * Account holder's state, or {@code null}
   */
  public String state;
  /**
   * Zip address, or {@code null}
   */
  public String zip;
}