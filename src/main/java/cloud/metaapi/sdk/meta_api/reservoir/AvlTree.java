package cloud.metaapi.sdk.meta_api.reservoir;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class AvlTree<T> {
  
  public class Node {
    public T key;
    public int weight;
    public int height;
    public Node left;
    public Node right;
  }
  
  private Node createNewNode(T k) {
    return new Node() {{
      key = k;
      weight = 1;
      height = 0;
      left = null;
      right = null;
    }};
  }
  
  private int height(Node p) {
    return p != null ? p.height : 0;
  }
  
  private int weight(Node p) {
    return p != null ? p.weight : 0;
  }
  
  private int bFactor(Node p) {
    return height(p.right) - height(p.left);
  }
  
  private void countHeightAndWeight(Node p) {
    int hl = height(p.left);
    int hr = height(p.right);
    p.height = (hl > hr ? hl : hr) + 1;
    
    int wl = weight(p.left);
    int wr = weight(p.right);
    p.weight = wl + wr + 1;
  }
  
  private Node rotateRight(Node p) {
    Node q = p.left;
    p.left = q.right;
    q.right = p;
    countHeightAndWeight(p);
    countHeightAndWeight(q);
    return q;
  }
  
  private Node rotateLeft(Node q) {
    Node p = q.right;
    q.right = p.left;
    p.left = q;
    countHeightAndWeight(q);
    countHeightAndWeight(p);
    return p;
  }
  
  private Node balance(Node p) {
    countHeightAndWeight(p);
    if (bFactor(p) == 2) {
      if (bFactor(p.right) < 0)
        p.right = rotateRight(p.right);
      return rotateLeft(p);
    }
    if (bFactor(p) == -2) {
      if (bFactor(p.left) > 0)
        p.left = rotateLeft(p.left);
      return rotateRight(p);
    }
    return p;
  }
  
  private int count(Node p, T k) {
    return upperBound(p, k) - lowerBound(p, k);
  }
  
  private T at(Node p, int k) {
    if (p == null) return null;
    int wl = weight(p.left);
    if (wl <= k && k < wl + 1) return p.key;
    else if (k < wl) return at(p.left, k);
    else return at(p.right, k - wl - 1);
  }
  
  private Node getMinimum(Node p) {
    if (p == null) return null;
    return p.left != null ? getMinimum(p.left) : p;
  }
  
  private Node getMaximum(Node p) {
    if (p == null) return null;
    return p.right != null ? getMaximum(p.right) : p;
  }
  
  private Node removeMinimum(Node p) {
    if (p.left == null) return p.right;
    p.left = removeMinimum(p.left);
    return balance(p);
  }
  
  private List<T> toList(Node p) {
    List<T> list = new ArrayList<>();
    if (p.left != null) list.addAll(toList(p.left));
    list.add(p.key);
    if (p.right != null) list.addAll(toList(p.right));
    return list;
  }
  
  public Node root = null;
  private Comparator<T> comparer;
  
  public AvlTree(Comparator<T> comparer) {
    this.comparer = comparer;
  }
  
  public int size() {
    return weight(root);
  }
  
  public T min() {
    Node p = getMinimum(root);
    if (p != null) return p.key;
    return null;
  }
  
  public T max() {
    Node p = getMaximum(root);
    if (p != null) return p.key;
    return null;
  }
  
  public int lowerBound(T k) {
    return lowerBound(root, k);
  }
  
  private int lowerBound(Node p, T k) {
    if (p == null) return 0;
    int cmp = comparer.compare(k, p.key);
    
    if (cmp <= 0) return lowerBound(p.left, k);
    else return weight(p.left) + lowerBound(p.right, k) + 1;
  }
  
  public int upperBound(T k) {
    return upperBound(root, k);
  }
  
  private int upperBound(Node p, T k) {
    if (p == null) return 0;
    int cmp = comparer.compare(k, p.key);
    
    if (cmp < 0) return upperBound(p.left, k);
    else return weight(p.left) + upperBound(p.right, k) + 1;
  }
  
  public int count(T k) {
    return count(root, k);
  }
  
  public T at(int k) {
    return at(root, k);
  }
  
  public void insert(T k) {
    root = insert(root, k);
  }
  
  private Node insert(Node p, T k) {
    if (p == null) return createNewNode(k);
    int cmp = comparer.compare(k, p.key);
    
    if (cmp < 0) p.left = insert(p.left, k);
    else p.right = insert(p.right, k);
    return balance(p);
  }
  
  public void remove(T k) {
    root = remove(root, k);
  }
  
  private Node remove(Node p, T k) {
    if (p == null) return null;
    int cmp = comparer.compare(k, p.key);
    
    if (cmp < 0) p.left = remove(p.left, k);
    else if (cmp > 0) p.right = remove(p.right, k);
    else {
      Node q = p.left;
      Node r = p.right;
      if (r == null) return q;
      
      Node min = getMinimum(r);
      min.right = removeMinimum(r);
      min.left = q;
      return balance(min);
    }
    return balance(p);
  }
  
  public void removeAt(int k) {
    T val = at(k);
    root = remove(root, val);
  }
  
  public List<T> toList() {
    if (root == null) return new ArrayList<>();
    return toList(root);
  }
}