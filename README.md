# Lucene-Relevance
Contains implementations of good similarities for Lucene.
- BM25CtfSimilarity: BM25-CTF uses collection term frequency to rank documents.
- BM25Similarity: additional BM25L and BM25PLUS models to improve long document scores.
- RobertsonSimilarity: tf-idf with Robertson's tf formula (BM25).
- LdpSimilarity: td-idf with TF = 1+ln(1+ln(tf)) and pivoted document length normalization.
- LtcSimilarity: tf-idf with sublinear tf (ltc). 
