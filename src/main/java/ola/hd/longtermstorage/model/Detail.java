package ola.hd.longtermstorage.model;


import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "detail", type = "detail")
public class Detail {

	@Field(type = FieldType.Text)
	private String title;
	@Field(type = FieldType.Integer)
	private int year;
	@Field(type = FieldType.Text)
	private String creator;
	@Field(type = FieldType.Text)
	private String publisher;
	@Field(type = FieldType.Text)
	private String fileTree;

	public Detail(String title, int year, String creator, String publisher, String fileTree) {
		this.title = title;
		this.year = year;
		this.creator = creator;
		this.publisher = publisher;
		this.fileTree = fileTree;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public int getYear() {
		return year;
	}

	public void setYear(int year) {
		this.year = year;
	}

	public String getCreator() {
		return creator;
	}

	public void setCreator(String creator) {
		this.creator = creator;
	}

	public String getPublisher() {
		return publisher;
	}

	public void setPublisher(String publisher) {
		this.publisher = publisher;
	}

	public String getFileTree() {
		return fileTree;
	}

	public void setFileTree(String fileTree) {
		this.fileTree = fileTree;
	}

}
