import {NgModule} from "@angular/core";
import {BrowserModule} from "@angular/platform-browser";
import {FormsModule} from "@angular/forms";
import {UtilitiesService} from "./utilities.service";
import {UtilitiesComponent} from "./utilities.component";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {PrevIncDialog} from "./prevInc.dialog";
import {ChartModule} from "../charting/chart.module";

@NgModule({
	imports : [
		BrowserModule,
		FormsModule,
		NgbModule,
		ChartModule,
	],
	declarations : [
		UtilitiesComponent,
		PrevIncDialog
	],
	entryComponents : [
		PrevIncDialog
	],
	providers : [
		UtilitiesService
	]
})
export class UtilitiesModule {}