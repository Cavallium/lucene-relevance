/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.novasearch.lucene.search.similarities;


import java.io.IOException;

import org.apache.lucene.index.AtomicReaderContext;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;

/**
 * BM25 Similarity. Introduced in Stephen E. Robertson, Steve Walker,
 * Susan Jones, Micheline Hancock-Beaulieu, and Mike Gatford. Okapi at TREC-3.
 * In Proceedings of the Third <b>T</b>ext <b>RE</b>trieval <b>C</b>onference (TREC 1994).
 * Gaithersburg, USA, November 1994.
 * <p/>
 * BM25L version. Introduced in
 * Yuanhua Lv, ChengXiang Zhai. "When Documents Are Very Long, BM25 Fails!".
 * In Proceedings of The 34th International ACM SIGIR conference on research
 * and development in Information Retrieval (SIGIR'11).
 */
public class BM25SimilarityDocLen extends Similarity {
  public enum BM25Model {
    CLASSIC, L
  }

  private final float k1;
  private final float b;
  private final float d;
  private final BM25Model model;

  /**
   * BM25 with the supplied parameter values.
   * @param k1 Controls non-linear term frequency normalization (saturation).
   * @param b Controls to what degree document length normalizes tf values.
   * @param d Controls document length normalization of tf values in BM25L.
   * @throws IllegalArgumentException if {@code k1} is infinite or negative, or if {@code b} is
   *         not within the range {@code [0..1]}, or if {@code d} is
   *         not within the range {@code [0..1]}
   */
  public BM25SimilarityDocLen(float k1, float b, float d, BM25Model model) {
    if (Float.isInfinite(k1) || k1 < 0) {
      throw new IllegalArgumentException("illegal k1 value: " + k1 + ", must be a non-negative finite value");
    }
    if (Float.isNaN(b) || b < 0 || b > 1) {
      throw new IllegalArgumentException("illegal b value: " + b + ", must be between 0 and 1");
    }
    if (Float.isNaN(d) || d < 0 || d > 1) {
      throw new IllegalArgumentException("illegal d value: " + d + ", must be between 0 and 1.5");
    }
    this.k1 = k1;
    this.b  = b;
    this.d  = d;
    this.model = model;
  }

  /**
   * BM25 with the supplied parameter values.
   * @param k1 Controls non-linear term frequency normalization (saturation).
   * @param b Controls to what degree document length normalizes tf values.
   * @throws IllegalArgumentException if {@code k1} is infinite or negative, or if {@code b} is
   *         not within the range {@code [0..1]}
   */
  public BM25SimilarityDocLen(float k1, float b) {
    if (Float.isInfinite(k1) || k1 < 0) {
      throw new IllegalArgumentException("illegal k1 value: " + k1 + ", must be a non-negative finite value");
    }
    if (Float.isNaN(b) || b < 0 || b > 1) {
      throw new IllegalArgumentException("illegal b value: " + b + ", must be between 0 and 1");
    }
    this.k1 = k1;
    this.b  = b;
    this.d  = 0;
    this.model = BM25Model.CLASSIC;
  }
  
  /** BM25 with these default values:
   * <ul>
   *   <li>{@code k1 = 1.2}</li>
   *   <li>{@code b = 0.75}</li>
   *   <li>{@code d = 0.5} for BM25L.</li>
   * </ul>
   */
  public BM25SimilarityDocLen(BM25Model model) {
    this.k1 = 1.2f;
    this.b  = 0.75f;
    if (model == BM25Model.L) {
      this.d = 0.5f;
    } else {
      this.d  = 0;
    }
    this.model = model;
  }
  
  /** BM25 with these default values:
   * <ul>
   *   <li>{@code k1 = 1.2}</li>
   *   <li>{@code b = 0.75}</li>
   * </ul>
   */
  public BM25SimilarityDocLen() {
    this.k1 = 1.2f;
    this.b  = 0.75f;
    this.d  = 0;
    this.model = BM25Model.CLASSIC;
  }
  
  /** Implemented as <code>log(1 + (numDocs - docFreq + 0.5)/(docFreq + 0.5))</code>. */
  protected float idf(long docFreq, long numDocs) {
    return (float) Math.log(1 + (numDocs - docFreq + 0.5D)/(docFreq + 0.5D));
  }
  
  /** Implemented as <code>1 / (distance + 1)</code>. */
  protected float sloppyFreq(int distance) {
    return 1.0f / (distance + 1);
  }
  
  /** The default implementation returns <code>1</code> */
  protected float scorePayload(int doc, int start, int end, BytesRef payload) {
    return 1;
  }
  
  /** The default implementation computes the average as <code>sumTotalTermFreq / maxDoc</code>,
   * or returns <code>1</code> if the index does not store sumTotalTermFreq (Lucene 3.x indexes
   * or any field that omits frequency information). */
  protected float avgFieldLength(CollectionStatistics collectionStats) {
    final long sumTotalTermFreq = collectionStats.sumTotalTermFreq();
    if (sumTotalTermFreq <= 0) {
      return 1f;       // field does not exist, or stat is unsupported
    } else {
      return (float) (sumTotalTermFreq / (double) collectionStats.maxDoc());
    }
  }
  
  /** 
   * True if overlap tokens (tokens with a position of increment of zero) are
   * discounted from the document's length.
   */
  protected boolean discountOverlaps = true;

  /** Sets whether overlap tokens (Tokens with 0 position increment) are 
   *  ignored when computing norm.  By default this is true, meaning overlap
   *  tokens do not count when computing norms. */
  public void setDiscountOverlaps(boolean v) {
    discountOverlaps = v;
  }

  /**
   * Returns true if overlap tokens are discounted from the document's length. 
   * @see #setDiscountOverlaps 
   */
  public boolean getDiscountOverlaps() {
    return discountOverlaps;
  }

  @Override
  public final long computeNorm(FieldInvertState state) {
    final int numTerms = discountOverlaps ? state.getLength() - state.getNumOverlap() : state.getLength();
    // Dividing by the square of the boost is to mimic behavior of the old BM25 formula
    final float boost = state.getBoost();
    return (long) ( numTerms / (boost*boost) );
  }

  /**
   * Computes a score factor for a simple term and returns an explanation
   * for that score factor.
   * 
   * <p>
   * The default implementation uses:
   * 
   * <pre class="prettyprint">
   * idf(docFreq, searcher.maxDoc());
   * </pre>
   * 
   * Note that {@link CollectionStatistics#maxDoc()} is used instead of
   * {@link org.apache.lucene.index.IndexReader#numDocs() IndexReader#numDocs()} because also 
   * {@link TermStatistics#docFreq()} is used, and when the latter 
   * is inaccurate, so is {@link CollectionStatistics#maxDoc()}, and in the same direction.
   * In addition, {@link CollectionStatistics#maxDoc()} is more efficient to compute
   *   
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the term
   * @return an Explain object that includes both an idf score factor 
             and an explanation for the term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats) {
    final long df = termStats.docFreq();
    final long max = collectionStats.maxDoc();
    final float idf = idf(df, max);
    return new Explanation(idf, "idf(docFreq=" + df + ", maxDocs=" + max + ")");
  }

  /**
   * Computes a score factor for a phrase.
   * 
   * <p>
   * The default implementation sums the idf factor for
   * each term in the phrase.
   * 
   * @param collectionStats collection-level statistics
   * @param termStats term-level statistics for the terms in the phrase
   * @return an Explain object that includes both an idf 
   *         score factor for the phrase and an explanation 
   *         for each term.
   */
  public Explanation idfExplain(CollectionStatistics collectionStats, TermStatistics termStats[]) {
    final long max = collectionStats.maxDoc();
    float idf = 0.0f;
    final Explanation exp = new Explanation();
    exp.setDescription("idf(), sum of:");
    for (final TermStatistics stat : termStats ) {
      final long df = stat.docFreq();
      final float termIdf = idf(df, max);
      exp.addDetail(new Explanation(termIdf, "idf(docFreq=" + df + ", maxDocs=" + max + ")"));
      idf += termIdf;
    }
    exp.setValue(idf);
    return exp;
  }

  @Override
  public final SimWeight computeWeight(float queryBoost, CollectionStatistics collectionStats, TermStatistics... termStats) {
    Explanation idf = termStats.length == 1 ? idfExplain(collectionStats, termStats[0]) : idfExplain(collectionStats, termStats);

    float avgdl = avgFieldLength(collectionStats);
    return new BM25Stats(collectionStats.field(), idf, queryBoost, avgdl);
  }

  @Override
  public final SimScorer simScorer(SimWeight stats, AtomicReaderContext context) throws IOException {
    BM25Stats bm25stats = (BM25Stats) stats;
    return new BM25DocScorer(bm25stats, context.reader().getNormValues(bm25stats.field));
  }
  
  private class BM25DocScorer extends SimScorer {
    private final BM25Stats stats;
    private final float weightValue; // boost * idf * (k1 + 1)
    private final NumericDocValues norms;
    /** precomputed k1 * ((1 - b) */
    private final float multK1minusB;
    /** precomputed k1 * b/avgdl. */
    private final float multK1_b_InvAvgdl;
    /** precomputed d / k1. */
    private final float multInvK1_d;

    BM25DocScorer(BM25Stats stats, NumericDocValues norms) throws IOException {
      this.stats = stats;
      this.weightValue = stats.weight * (k1 + 1);
      this.norms = norms;
      this.multK1minusB = k1 * (1 - b);
      this.multK1_b_InvAvgdl = k1 * b / stats.avgdl;
      this.multInvK1_d = d / k1;
    }
    
    @Override
    public float score(int doc, float freq) {
      float norm;
      float multInvK1_d_norm = 0;
      if (norms == null) {
        // if there are no norms, we act as if b=0
        norm = k1;
      } else {
        float doclen = norms.get(doc);
        norm = multK1minusB + multK1_b_InvAvgdl * doclen;
        multInvK1_d_norm = multInvK1_d * norm;
        }
      return weightValue * (freq + multInvK1_d_norm) / (freq + norm + multInvK1_d_norm);
    }
    
    @Override
    public Explanation explain(int doc, Explanation freq) {
      return explainScore(doc, freq, stats, norms);
    }

    @Override
    public float computeSlopFactor(int distance) {
      return sloppyFreq(distance);
    }

    @Override
    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
      return scorePayload(doc, start, end, payload);
    }
  }
  
  /** Collection statistics for the BM25 model. */
  private static class BM25Stats extends SimWeight {
    /** BM25's idf */
    private final Explanation idf;
    /** The average document length. */
    private final float avgdl;
    /** query's inner boost */
    private final float queryBoost;
    /** query's outer boost (only for explain) */
    private float topLevelBoost;
    /** weight (idf * boost) */
    private float weight;
    /** field name, for pulling norms */
    private final String field;

    BM25Stats(String field, Explanation idf, float queryBoost, float avgdl) {
      this.field = field;
      this.idf = idf;
      this.queryBoost = queryBoost;
      this.avgdl = avgdl;
    }

    @Override
    public float getValueForNormalization() {
      // we return a TF-IDF like normalization to be nice, but we don't actually normalize ourselves.
      final float queryWeight = idf.getValue() * queryBoost;
      return queryWeight * queryWeight;
    }

    @Override
    public void normalize(float queryNorm, float topLevelBoost) {
      // we don't normalize with queryNorm at all, we just capture the top-level boost
      this.topLevelBoost = topLevelBoost;
      this.weight = idf.getValue() * queryBoost * topLevelBoost;
    } 
  }
  
  private Explanation explainScore(int doc, Explanation freq, BM25Stats stats, NumericDocValues norms) {
    Explanation result = new Explanation();
    result.setDescription("score(doc="+doc+",freq="+freq+"), product of:");
    
    Explanation boostExpl = new Explanation(stats.queryBoost * stats.topLevelBoost, "boost");
    if (boostExpl.getValue() != 1.0f)
      result.addDetail(boostExpl);
    
    result.addDetail(stats.idf);

    Explanation tfNormExpl = new Explanation();
    tfNormExpl.setDescription("tfNorm, computed from:");
    tfNormExpl.addDetail(freq);
    tfNormExpl.addDetail(new Explanation(k1, "parameter k1"));
    if (norms == null) {
      tfNormExpl.addDetail(new Explanation(0, "parameter b (norms omitted for field)"));
      tfNormExpl.setValue((freq.getValue() * (k1 + 1)) / (freq.getValue() + k1));
    } else {
      float doclen = norms.get(doc);
      tfNormExpl.addDetail(new Explanation(b, "parameter b"));
      tfNormExpl.addDetail(new Explanation(stats.avgdl, "avgFieldLength"));
      tfNormExpl.addDetail(new Explanation(doclen, "fieldLength"));
      if (model == BM25Model.L) {
        tfNormExpl.addDetail(new Explanation(d, "parameter d"));
        float value = d + freq.getValue() / (1 - b + b * doclen/stats.avgdl);
        tfNormExpl.setValue(((k1 + 1) * value) / (k1 + value));
      } else {
        tfNormExpl.setValue((freq.getValue() * (k1 + 1)) / (freq.getValue() + k1 * (1 - b + b * doclen/stats.avgdl)));
      }
    }
    result.addDetail(tfNormExpl);
    result.setValue(boostExpl.getValue() * stats.idf.getValue() * tfNormExpl.getValue());
    return result;
  }

  @Override
  public String toString() {
    if (model == BM25Model.CLASSIC) {
      return "BM25(k1=" + k1 + ",b=" + b + ")";
    } else {
      return "BM25" + model.toString() + "(k1=" + k1 + ",b=" + b + ",d=" + d + ")";
    }
  }
  
  /** 
   * Returns the <code>k1</code> parameter
   * @see #BM25SimilarityDocLen(float, float)
   */
  public final float getK1() {
    return k1;
  }
  
  /**
   * Returns the <code>b</code> parameter 
   * @see #BM25SimilarityDocLen(float, float)
   */
  public final float getB() {
    return b;
  }
  
  /**
   * Returns the <code>d</code> parameter 
   * @see #BM25SimilarityDocLen(float, float, float, BM25Model)
   */
  public final float getD() {
    return d;
  }
}
