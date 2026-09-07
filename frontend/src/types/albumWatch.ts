export interface AlbumWatch {
  id: number;
  albumId: number;
  albumTitle: string;
  createdAt: string;
}

export interface AlbumWatchListResponse {
  content: AlbumWatch[];
}
