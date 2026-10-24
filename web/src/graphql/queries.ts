import { gql } from "@apollo/client";
import type { TypedDocumentNode } from "@apollo/client";
import type { SearchQueryResult, SearchQueryVars } from "./types";

export const SEARCH_QUERY: TypedDocumentNode<SearchQueryResult, SearchQueryVars> = gql`
  query Search($query: String!, $topK: Int, $strategy: RecommenderStrategy, $userId: String) {
    search(query: $query, topK: $topK, strategy: $strategy, userId: $userId) {
      query
      strategy
      products {
        productId
        name
        productClass
        categoryHierarchy
        averageRating
        ratingCount
        score
        source
      }
    }
  }
`;
