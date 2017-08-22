import {Component} from "@angular/core";
import {NgbModal} from "@ng-bootstrap/ng-bootstrap";
import {StateService} from "ui-router-ng2";
import {UtilitiesService} from "./utilities.service";
import {LoggerService} from "eds-common-js";
import {PrevIncDialog} from "./prevInc.dialog";
import {PrevInc} from "./models/PrevInc";
import {Chart} from "../charting/models/Chart";
import {Series} from "../charting/models/Series";
import {ChartDialog} from "../charting/chart.dialog";

@Component({
	template : require('./utilities.html')
})
export class UtilitiesComponent {

	constructor(private utilitiesService:UtilitiesService,
							private logger:LoggerService,
							private $modal: NgbModal,
							private $state : StateService) {
	}

	pi () {
		let vm = this;
		let prevInc: PrevInc = {
			organisation: [],
			population: ""
		};
		PrevIncDialog.open(vm.$modal, prevInc).result.then(
			(result) => {
				console.log(result);
			},
			(error) => vm.logger.error("Error running utility", error)
		);
	}

	chart () {
		let chartData = new Chart()
			.setTitle('Results')
			.setCategories(['2001', '2002', '2003', '2004', '2005'])
			.setSeries([
				new Series()
					.setType('column')
					.setName('Male')
					.setData([3, 2, 1, 3, 4]),
				new Series()
					.setType('column')
					.setName('Female')
					.setData([2, 3, 5, 7, 6]),
				new Series()
					.setType('spline')
					.setName('T')
					.setData([3, 2.67, 3, 6.33, 3.33]),
				new Series()
					.setType('pie')
					.setName('Prevalence')
					.setSize(100)
					.setCenter(100,80)
					.setData([
						{name: 'Male', y: 13,},
						{name: 'Female', y: 23,}
					])
			]);

		ChartDialog.open(this.$modal, chartData);
	}
}

