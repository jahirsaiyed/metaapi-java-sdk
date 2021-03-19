package cloud.metaapi.sdk.clients.meta_api.models;

/**
 * MetaTrader order book entry
 */
public class MetatraderBookEntry {
  
  /**
   * Entry type
   */
  public enum BookType { BOOK_TYPE_SELL, BOOK_TYPE_BUY, BOOK_TYPE_SELL_MARKET, BOOK_TYPE_BUY_MARKET };
  
  /**
   * Entry type
   */
  public BookType type;
  /**
   * Price
   */
  public double price;
  /**
   * Volume
   */
  public double volume;
}