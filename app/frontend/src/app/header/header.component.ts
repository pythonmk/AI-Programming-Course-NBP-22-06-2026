import { Component } from '@angular/core';

@Component({
  selector: 'app-header',
  standalone: true,
  imports: [],
  template: `
    <header class="nbp-header">
      <div class="nbp-header__inner">
        <img
          class="nbp-logo"
          src="logo.svg"
          alt="Narodowy Bank Polski"
          height="40"
        />
        <span class="header-title">Asystent reklamacji i zwrotów</span>
      </div>
    </header>
  `,
  styles: [`
    .nbp-header {
      background-color: #152E52;
      width: 100%;
      border-radius: 0;
    }

    .nbp-header__inner {
      display: flex;
      align-items: center;
      gap: 24px;
      padding: 12px 24px;
    }

    .nbp-logo {
      height: 40px;
      width: auto;
      display: block;
    }

    .header-title {
      color: #FFFFFF;
      font-family: "Libre Franklin", -apple-system, Arial, sans-serif;
      font-size: 16px;
      font-weight: 500;
      letter-spacing: 0.01em;
    }
  `],
})
export class HeaderComponent {}
