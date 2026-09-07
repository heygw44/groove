export interface ProductSuggestion {
  id: number;
  title: string;
  artistName: string;
  thumbnailUrl?: string;
}

export interface ArtistSuggestion {
  id: number;
  name: string;
}

export interface SearchSuggestions {
  products: ProductSuggestion[];
  artists: ArtistSuggestion[];
}
