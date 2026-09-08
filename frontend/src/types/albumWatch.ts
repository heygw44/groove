export interface AlbumWatch {
  id: number;
  albumId: number;
  albumTitle: string;
  createdAt: string;
}

export interface AlbumWatchListParams {
  page?: number;
  size?: number;
}
