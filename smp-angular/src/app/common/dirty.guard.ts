import {Injectable} from '@angular/core';
import {ActivatedRouteSnapshot, CanActivate, CanDeactivate, RouterStateSnapshot} from '@angular/router';
import {Observable} from 'rxjs';
import {MatDialog} from '@angular/material/dialog';
import {CancelDialogComponent} from './dialogs/cancel-dialog/cancel-dialog.component';

@Injectable()
export class DirtyGuard implements CanActivate, CanDeactivate<any> {

  constructor(public dialog: MatDialog) {

  };

  canActivate(next: ActivatedRouteSnapshot,
              state: RouterStateSnapshot): Observable<boolean> | Promise<boolean> | boolean {
    return true;
  }

  canDeactivate(component: any, currentRoute: ActivatedRouteSnapshot, currentState: RouterStateSnapshot,
                nextState?: RouterStateSnapshot): Observable<boolean> | Promise<boolean> | boolean {
    if (component.isDirty && !component.isDirty()) return true;
    return this.dialog.open(CancelDialogComponent).afterClosed();
  }
}
