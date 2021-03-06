import {Component, Input, OnInit} from "@angular/core";
import {NgbModal, NgbActiveModal} from "@ng-bootstrap/ng-bootstrap";
import {Chart} from "./models/Chart";

@Component({
	selector: 'ngbd-modal-content',
	template: require('./table-dialog.html')
})
export class TableDialog implements OnInit {

	public static open(modalService: NgbModal, title : string, tableData : any) {
		const modalRef = modalService.open(TableDialog, { backdrop : "static", size : "lg"});
		modalRef.componentInstance.title = title;
		modalRef.componentInstance.tableData = tableData;

		return modalRef;
	}

	@Input() title;
	@Input() tableData;

	constructor(protected $uibModalInstance : NgbActiveModal) {
	}

	ngOnInit(): void {
	}

	export() {
		this.tableData.export();
	}

	cancel() {
		this.$uibModalInstance.close(null);
	}
}
