module app.models {
	'use strict';

	export class Folder {
		uuid:string;
		folderName:string;
		folderType:number;
		parentFolderUuid:string;
		hasChildren:boolean;
		contentCount:number;
	}
}