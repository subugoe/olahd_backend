package ola.hd.longtermstorage.model;

import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "resultSet", type = "resultSet")
public class ResultSet {

		@Field(type = FieldType.Integer)
		private int hits;
		@Field(type = FieldType.Integer)
		private int offset;
		@Field(type = FieldType.Integer)
		private	int limit;
		@Field(type = FieldType.Boolean)
		private	boolean metadataSearch;
		@Field(type = FieldType.Boolean)
		private	boolean fulltextSearch;
	 	@Field(type = FieldType.Text)
		private	String searchTerm;
		@Field(type = FieldType.Text)
		private HitList hitlist;
		@Field(type = FieldType.Text)
		private Facets facets;

	    public ResultSet(String searchTerm, int hits, int offset, int limit, boolean metadataSearch, boolean fulltextSearch, HitList hitlist, Facets facets ) {
	        this.hits = hits;
	        this.offset = offset;
	        this.limit = limit;
			this.metadataSearch = metadataSearch;
			this.fulltextSearch = fulltextSearch;
			this.searchTerm = searchTerm;
			this.hitlist = hitlist;
			this.facets = facets;
		}

		public int getHits() {
			return hits;
		}

		public void setHits(int hits) {
			this.hits = hits;
		}

		public int getOffset() {
			return offset;
		}

		public void setOffset(int offset) {
			this.offset = offset;
		}

		public int getLimit() {
			return limit;
		}

		public void setLimit(int limit) {
			this.limit = limit;
		}

		public boolean isMetadataSearch() {
			return metadataSearch;
		}

		public void setMetadataSearch(boolean metadataSearch) {
			this.metadataSearch = metadataSearch;
		}

		public boolean isFulltextSearch() {
			return fulltextSearch;
		}

		public void setFulltextSearch(boolean fulltextSearch) {
			this.fulltextSearch = fulltextSearch;
		}

		public String getSearchTerm() {
			return searchTerm;
		}

		public void setSearchTerm(String searchTerm) {
			this.searchTerm = searchTerm;
		}

		public HitList getHitlist() {
			return hitlist;
		}

		public void setHitlist(HitList hitlist) {
			this.hitlist = hitlist;
		}

		public Facets getFacets() {
			return facets;
		}

		public void setFacets(Facets facets) {
			this.facets = facets;
		}
}
